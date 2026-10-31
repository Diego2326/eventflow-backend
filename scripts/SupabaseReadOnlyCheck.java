import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.internal.resource.filesystem.FileSystemResource;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;

/** Loads Boot config without an application context, server, Flyway initializer or jobs. */
class SupabaseReadOnlyCheck {
    public static void main(String[] args) {
        String stage = "configuration";
        try {
            var env = new StandardEnvironment();
            if (Arrays.asList(args).contains("--local")) env.setActiveProfiles("local");
            ConfigDataEnvironmentPostProcessor.applyTo(env);
            System.out.println("active_profiles=" + String.join(",", env.getActiveProfiles()));
            System.out.println("flyway_enabled=" + env.getProperty("spring.flyway.enabled", "true (default)"));
            System.out.println("baseline_on_migrate=" + env.getProperty("spring.flyway.baseline-on-migrate", "false (default)"));
            System.out.println("hibernate_ddl=" + env.getProperty("spring.jpa.hibernate.ddl-auto", "unset"));
            for (String key : List.of("DB_URL","DB_USERNAME","DB_PASSWORD","JWT_SECRET"))
                System.out.println(key + "_present=" + (env.getProperty(key) != null && !env.getProperty(key).isBlank()));
            if (!"false".equalsIgnoreCase(env.getProperty("spring.flyway.enabled")))
                throw new IllegalStateException("Flyway must be explicitly disabled for this check");
            String url = env.getRequiredProperty("spring.datasource.url");
            if (!url.startsWith("jdbc:postgresql:")) throw new IllegalStateException("PostgreSQL URL required");
            if (url.matches("(?is).*sslmode=(disable|allow|prefer).*")) throw new IllegalStateException("TLS required");
            var props = new Properties();
            props.setProperty("user", env.getRequiredProperty("spring.datasource.username"));
            props.setProperty("password", env.getRequiredProperty("spring.datasource.password"));
            props.setProperty("sslmode", "require");
            props.setProperty("readOnlyMode", "always");
            props.setProperty("connectTimeout", "10");
            props.setProperty("socketTimeout", "20");
            stage = "connection";
            try (Connection db = DriverManager.getConnection(url, props)) {
                // pg_stat_ssl may describe a pooler-to-server hop; inspect our own socket too.
                var executor = db.unwrap(org.postgresql.core.BaseConnection.class).getQueryExecutor();
                var streamField = org.postgresql.core.QueryExecutorBase.class.getDeclaredField("pgStream");
                streamField.setAccessible(true);
                var socket = ((org.postgresql.core.PGStream)streamField.get(executor)).getSocket();
                System.out.println("client_tls=" + (socket instanceof javax.net.ssl.SSLSocket));
                if (socket instanceof javax.net.ssl.SSLSocket ssl) System.out.println("client_tls_protocol="+ssl.getSession().getProtocol());
                db.setReadOnly(true);
                db.setAutoCommit(false);
                query(db, "connection_state", "SELECT current_setting('transaction_read_only') AS read_only, current_setting('server_version') AS server_version, (SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()) AS tls");
                if (Arrays.asList(args).contains("--audit")) {
                    stage = "security_audit";
                    int number=0;
                    for (String sql : Files.readAllLines(Path.of("scripts/supabase-security-audit.sql"))) {
                        if (sql.isBlank() || sql.stripLeading().startsWith("--")) continue;
                        query(db,"audit_"+(++number),sql);
                    }
                    db.rollback();
                    System.out.println("finished=READ_ONLY_ROLLBACK; audit only");
                    return;
                }
                stage = "history";
                Map<String, List<String>> history = new HashMap<>();
                try (var st = db.createStatement(); var rs = st.executeQuery("SELECT version,script,description,checksum,success,type FROM public.flyway_schema_history ORDER BY installed_rank")) {
                    while(rs.next()) {
                        String version=rs.getString(1);
                        history.put(version, Arrays.asList(rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)));
                    }
                }
                System.out.println("history_rows=" + history.size());
                Path dir=Path.of("src/main/resources/db/"+(Arrays.asList(args).contains("--safe")?"migration-safe":"migration")).toAbsolutePath();
                for(int version=1;version<=26;version++) {
                    final int v=version;
                    Path file;
                    try(var files=Files.list(dir)) { file=files.filter(p->p.getFileName().toString().startsWith("V"+v+"__")).findFirst().orElseThrow(); }
                    String name=file.getFileName().toString();
                    String description=name.substring(name.indexOf("__")+2,name.length()-4).replace('_',' ');
                    int checksum=ChecksumCalculator.calculate(new FileSystemResource(new Location("filesystem:"+dir),file.toString(),StandardCharsets.UTF_8,false));
                    var h=history.get(Integer.toString(version));
                    String status=h==null?"PENDING":(h.get(0).equals(name)&&h.get(1).equals(description)&&h.get(2).equals(Integer.toString(checksum))&&"t".equals(h.get(3))&&"SQL".equals(h.get(4))?"MATCH":"MISMATCH");
                    System.out.println("V"+version+" local_checksum="+checksum+" db_checksum="+(h==null?"none":h.get(2))+" status="+status);
                }
                stage = "schema_inspection";
                query(db,"tables","SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname='public' ORDER BY tablename");
                query(db,"user_columns","SELECT column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='users' ORDER BY ordinal_position");
                query(db,"column_sizes","SELECT table_name,column_name,character_maximum_length FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('users','country','phone_prefix') AND character_maximum_length IS NOT NULL ORDER BY table_name,ordinal_position");
                query(db,"constraints","SELECT c.relname AS table_name,pg_get_constraintdef(k.oid) AS definition FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname IN ('users','country','phone_prefix') ORDER BY c.relname,k.conname");
                query(db,"data_api_grants","SELECT grantee,table_name,privilege_type FROM information_schema.role_table_grants WHERE table_schema='public' AND grantee IN ('anon','authenticated') ORDER BY grantee,table_name,privilege_type");
                query(db,"counts","SELECT (SELECT count(*) FROM public.users) AS users, (SELECT count(*) FROM public.country) AS countries, (SELECT count(*) FROM public.phone_prefix) AS phone_prefixes");
                query(db,"duplicate_phone_groups","SELECT count(*) AS conflicts FROM (SELECT user_phone_prefix_id,user_phone_number FROM public.users WHERE user_phone_number IS NOT NULL AND user_phone_prefix_id IS NOT NULL GROUP BY user_phone_prefix_id,user_phone_number HAVING count(*)>1) d");
                query(db,"demo_conflicts","SELECT count(*) AS conflicts FROM public.users WHERE user_id IN ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333') OR lower(user_email) IN ('demo.organizador@eventflow.gt','demo.invitada@eventflow.gt','demo.proveedor@eventflow.gt')");
                query(db,"extension","SELECT name,default_version,installed_version FROM pg_available_extensions WHERE name='btree_gist'");
                query(db,"permissions","SELECT has_schema_privilege(current_user,'public','CREATE') AS create_public,has_database_privilege(current_user,current_database(),'CREATE') AS create_objects,(SELECT pg_has_role(current_user,tableowner,'USAGE') FROM pg_tables WHERE schemaname='public' AND tablename='users') AS owns_users");
                db.rollback();
                System.out.println("finished=READ_ONLY_ROLLBACK; no application context or migration engine started");
            }
        } catch(Exception ex) {
            // Never emit exception messages/stack traces: JDBC/config errors can contain credentials.
            System.out.println("FAILED stage="+stage+" exception="+ex.getClass().getSimpleName()+" sqlstate="+(ex instanceof SQLException sql?sql.getSQLState():"n/a"));
            System.exit(1);
        }
    }
    static void query(Connection db,String label,String sql) throws SQLException {
        try(var st=db.createStatement();var rs=st.executeQuery(sql)) {
            var meta=rs.getMetaData();
            while(rs.next()) {
                var line=new StringJoiner(" ",label+": ","");
                for(int i=1;i<=meta.getColumnCount();i++) line.add(meta.getColumnLabel(i)+"="+rs.getString(i));
                System.out.println(line);
            }
        }
    }
}
