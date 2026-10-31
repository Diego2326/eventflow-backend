import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/** LOCAL ONLY: never reads .env or constructs a remote connection. */
class LocalMigrationRehearsal {
    static final String USER="eventflow_rehearsal_owner", DB="eventflow_migration_check";
    static final String URL="jdbc:postgresql://127.0.0.1:55432/"+DB;
    static final Path BIN=Path.of("C:/Program Files/PostgreSQL/18/bin");
    static Path root,cluster; static String password,migratorPassword,runtimePassword; static boolean started=false;
    static boolean safe;
    static String stage="setup";
    public static void main(String[] args) {
        int exit=0;
        // Disable framework SQL/exception logging; results below never include row values.
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        try {
            safe=Arrays.asList(args).contains("--safe-roles");
            root=Files.createDirectory(Path.of("backups","migration-rehearsal-"+System.currentTimeMillis())).toAbsolutePath();
            System.out.println("rehearsal_directory="+root);
            Path source=Path.of("backups/20261030T225844211Z/eventflow-public.dump").toAbsolutePath();
            String expected=Files.readString(Path.of(source+".sha256")).split("\\s+")[0];
            if(!expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)))))throw new IllegalStateException();
            System.out.println("verified_archive_sha256=OK; remote_connections=NONE");
            password=Base64.getEncoder().encodeToString(SecureRandom.getSeed(36));
            Path pw=root.resolve("init-password.tmp");Files.writeString(pw,password);cluster=root.resolve("cluster");
            stage="initdb";
            try {require(BackupRestoreCheck.tool("initdb",List.of("-D",cluster.toString(),"-U",USER,"--auth=scram-sha-256","--pwfile="+pw,"--encoding=UTF8","--locale=C"),Map.of()));}
            finally{Files.deleteIfExists(pw);}
            stage="start";
            require(BackupRestoreCheck.tool("pg_ctl",List.of("-D",cluster.toString(),"-l",root.resolve("server.log").toString(),"-o","-h 127.0.0.1 -p 55432 -c log_statement=none -c log_min_messages=panic -c log_min_error_statement=panic","-w","start"),Map.of()));started=true;
            require(BackupRestoreCheck.tool("createdb",List.of("-h","127.0.0.1","-p","55432","-U",USER,"-w",DB),localEnv()));
            stage="restore";
            var proc=new ProcessBuilder(BIN.resolve("pg_restore.exe").toString(),"--list",source.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String toc=new String(proc.getInputStream().readAllBytes(),StandardCharsets.UTF_8);require(proc.waitFor()==0);
            Path list=root.resolve("restore-toc.txt");Files.writeString(list,toc.lines().filter(s->!s.matches("^\\d+; \\d+ \\d+ SCHEMA - public .*$")).reduce("",(a,b)->a+b+"\n"));
            require(BackupRestoreCheck.tool("pg_restore",List.of("-h","127.0.0.1","-p","55432","-U",USER,"-d",DB,"-w","--single-transaction","--exit-on-error","--no-owner","--no-privileges","--use-list="+list,source.toString()),localEnv()));
            List<String> ids=new ArrayList<>();String original;
            try(var db=connection();var st=db.createStatement();var rs=st.executeQuery("SELECT user_id::text FROM users ORDER BY user_id")){while(rs.next())ids.add(rs.getString(1));original=originalFingerprint(db,ids);}
            require(ids.size()==2);
            if(safe) {
                stage="dedicated_roles";
                migratorPassword=Base64.getEncoder().encodeToString(SecureRandom.getSeed(36));
                runtimePassword=Base64.getEncoder().encodeToString(SecureRandom.getSeed(36));
                try(var db=connection();var st=db.createStatement()) {
                    // Random Base64 cannot contain SQL quotes; credentials never reach logs.
                    st.execute("CREATE ROLE eventflow_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD '"+migratorPassword+"'");
                    st.execute("CREATE ROLE eventflow_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD '"+runtimePassword+"'");
                    st.execute("CREATE ROLE anon NOLOGIN; CREATE ROLE authenticated NOLOGIN");
                    String bootstrap=Files.readAllLines(Path.of("scripts/bootstrap-eventflow-roles.sql")).stream()
                        .filter(l->!l.stripLeading().startsWith("\\")&&!l.startsWith("CREATE ROLE "))
                        .reduce("",(a,b)->a+b+"\n").replace(":\"database_name\"","\""+DB+"\"");
                    st.execute(bootstrap);
                }
                System.out.println("safe_variant=true; dedicated_migrator=NON_SUPERUSER; runtime=NON_SUPERUSER_NO_DDL");
            }
            var initial=flyway("4");initial.validate();System.out.println("V1-V4 validation=OK; originals=2");
            stage="migrations";
            for(int version=5;version<=26;version++) {
                stage="V"+version;
                var result=flyway(Integer.toString(version)).migrate();
                String line="V"+version+" success="+result.success+" executed="+result.migrationsExecuted;
                System.out.println(line);Files.writeString(root.resolve("migration-results.txt"),line+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                if(version==5)try(var db=connection()){
                    require(original.equals(originalFingerprint(db,ids)));
                    var a=db.createArrayOf("uuid",ids.toArray());
                    try(var p=db.prepareStatement("SELECT count(*) AS originals,count(*) FILTER(WHERE user_status='PENDING_VERIFICATION') AS pending,count(*) FILTER(WHERE user_email_verified_at IS NOT NULL) AS verified,(SELECT count(*) FROM user_role WHERE user_id=ANY(?)) AS roles FROM users WHERE user_id=ANY(?)")){
                        p.setArray(1,a);p.setArray(2,a);try(var r=p.executeQuery()){r.next();System.out.println("V5 originals="+r.getInt(1)+" pending="+r.getInt(2)+" verified="+r.getInt(3)+" roles="+r.getInt(4)+" original_fields_unchanged=true");}
                    }
                }
                if(version==7||version==11)try(var db=connection();var st=db.createStatement();var rs=st.executeQuery("SELECT indexname FROM pg_indexes WHERE schemaname='public' AND indexname LIKE 'ux_module_action%' ORDER BY indexname")){while(rs.next())System.out.println("V"+version+" index="+rs.getString(1));}
            }
            stage="post_migration";
            flyway("26").validate();
            try(var db=connection()) {
                require(original.equals(originalFingerprint(db,ids)));System.out.println("original_users_fields_preserved=true");
                var st=db.createStatement();
                try(var r=st.executeQuery("SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank")){while(r.next())System.out.println("history V"+r.getString(1)+" checksum="+r.getInt(2)+" success="+r.getBoolean(3));}
                try(var r=st.executeQuery("SELECT extversion FROM pg_extension WHERE extname='btree_gist'")){r.next();System.out.println("btree_gist_installed="+r.getString(1));}
                try(var r=st.executeQuery("SELECT count(*) FROM pg_constraint WHERE conname='reservation_no_accepted_overlap'")){r.next();System.out.println("reservation_exclusion_constraints="+r.getInt(1));}
                try(var r=st.executeQuery("SELECT count(*) AS demo_users,count(*) FILTER(WHERE user_status='ACTIVE') AS active,(SELECT count(*) FROM user_role WHERE role='ADMIN') AS admins FROM users WHERE user_id IN ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333')")){r.next();System.out.println("demo_users="+r.getInt(1)+" active="+r.getInt(2)+" admins="+r.getInt(3));}
                if(safe)try(var r=st.executeQuery("SELECT count(*) FROM users WHERE user_id=ANY(ARRAY['11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333']::uuid[])")){r.next();require(r.getInt(1)==0);System.out.println("safe_seed_assertion=NO_DEMO_ACCOUNTS");}
                try(var r=st.executeQuery("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r' AND NOT c.relrowsecurity")){r.next();System.out.println("local_tables_without_rls="+r.getInt(1));}
                try(var r=st.executeQuery("SELECT count(*) FROM information_schema.role_table_grants WHERE table_schema='public' AND grantee IN ('anon','authenticated')")){r.next();System.out.println("local_api_grants="+r.getInt(1)+" (source ACL deliberately not restored)");}
            }
            if(safe) {
                stage="runtime_grants";
                require(runMigratorSql("scripts/grant-eventflow-runtime.sql",List.of()));
                try(var db=connection();var st=db.createStatement()) {
                    st.execute("GRANT ALL ON ALL TABLES IN SCHEMA public TO anon,authenticated; GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon,authenticated; ALTER DEFAULT PRIVILEGES FOR ROLE eventflow_migrator IN SCHEMA public GRANT ALL ON TABLES TO anon,authenticated; ALTER DEFAULT PRIVILEGES FOR ROLE eventflow_migrator IN SCHEMA public GRANT ALL ON SEQUENCES TO anon,authenticated");
                }
                require(runMigratorSql("scripts/review-harden-eventflow.sql",List.of("-v","backend_role=eventflow_runtime","-v","creator_role=eventflow_migrator","-v","apply=true")));
                System.out.println("hardening_script=COMMITTED_AND_VALIDATED_LOCAL_ONLY");
                stage="safe_postflight";
                require(runMigratorSql("scripts/validate-safe-deployment.sql",List.of()));
                System.out.println("safe_postflight=OK_LOCAL_ONLY");
                try(var db=DriverManager.getConnection(URL,"eventflow_runtime",runtimePassword)) {
                    db.setAutoCommit(false);
                    for(String sql:List.of("SELECT * FROM public.flyway_schema_history","CREATE TABLE public.__runtime_ddl_probe(id int)")) {
                        boolean denied=false;
                        try(var st=db.createStatement()){st.execute(sql);}catch(SQLException e){denied="42501".equals(e.getSQLState());}
                        db.rollback();require(denied);
                    }
                    System.out.println("runtime_history_access=DENIED; runtime_DDL=DENIED");
                }
            }
            stage="hibernate_validate";
            var emf=new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(new DriverManagerDataSource(URL,safe?"eventflow_runtime":USER,safe?runtimePassword:password));
            emf.setPackagesToScan("com.eventflow.eventflow_api");emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            emf.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.show_sql","false"));
            emf.afterPropertiesSet();emf.destroy();System.out.println("hibernate_validate=OK");
            stage="tests";
            require(BackupRestoreCheck.tool("createdb",List.of("-h","127.0.0.1","-p","55432","-U",USER,"-w","--template="+DB,"eventflow_tests_check"),localEnv()));
            if(safe)try(var db=connection();var st=db.createStatement()){st.execute("GRANT CONNECT ON DATABASE eventflow_tests_check TO eventflow_runtime");}
            var pb=new ProcessBuilder("C:/Windows/System32/cmd.exe","/c","gradlew.bat","test","--rerun-tasks","--console=plain");
            var e=pb.environment();e.putAll(localEnv());
            String testUrl="jdbc:postgresql://127.0.0.1:55432/eventflow_tests_check";
            String testUser=safe?"eventflow_runtime":USER, testPassword=safe?runtimePassword:password;
            e.put("SPRING_DATASOURCE_URL",testUrl);e.put("SPRING_DATASOURCE_USERNAME",testUser);e.put("SPRING_DATASOURCE_PASSWORD",testPassword);e.put("SPRING_DATASOURCE_DRIVER_CLASS_NAME","org.postgresql.Driver");
            e.put("SPRING_JPA_HIBERNATE_DDL_AUTO","validate");e.put("SPRING_FLYWAY_ENABLED","false");e.put("SPRING_CONFIG_IMPORT","");e.put("SPRING_PROFILES_ACTIVE","local");e.put("BOOTSTRAP_ADMIN_EMAIL","");e.put("LOGGING_LEVEL_ROOT","OFF");
            e.put("DB_URL",testUrl);e.put("DB_USERNAME",testUser);e.put("DB_PASSWORD",testPassword);
            e.put("JAVA_HOME","C:/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot");e.put("GRADLE_USER_HOME","C:/Users/sanch/.gradle");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            int testExit=pb.start().waitFor();
            int[] testCounts=copyTestReports(root.resolve("postgresql-test-results"));
            System.out.println("postgresql_test_exit="+testExit+" tests="+testCounts[0]+" failures="+testCounts[1]+" errors="+testCounts[2]+" skipped="+testCounts[3]);
            Files.writeString(root.resolve("postgresql-test-summary.txt"),"tests="+testCounts[0]+" failures="+testCounts[1]+" errors="+testCounts[2]+" skipped="+testCounts[3]+" exit="+testExit+"\n");
            Files.writeString(root.resolve("result.txt"),"migrations=V1-V26_VALID\noriginal_users_preserved=true\nhibernate_validate=OK\npostgresql_test_exit="+testExit+"\npostgresql_tests="+testCounts[0]+"\npostgresql_failures="+(testCounts[1]+testCounts[2])+"\npostgresql_skipped="+testCounts[3]+"\n");
            if(testExit!=0||testCounts[0]==0||testCounts[1]!=0||testCounts[2]!=0||testCounts[3]!=0)exit=1;
        } catch(Exception ex) {
            exit=1;Throwable cause=ex;while(cause.getCause()!=null)cause=cause.getCause();
            System.out.println("FAILED stage="+stage+" exception="+ex.getClass().getSimpleName()+" root="+cause.getClass().getSimpleName()+" sqlstate="+(cause instanceof SQLException s?s.getSQLState():"n/a"));
        } finally {
            if(started)System.out.println("isolated_server_stopped="+BackupRestoreCheck.tool("pg_ctl",List.of("-D",cluster.toString(),"-w","stop","-m","fast"),Map.of()));
        }
        System.exit(exit);
    }
    static Map<String,String> localEnv(){return Map.of("PGPASSWORD",password,"PGSSLMODE","disable");}
    static Connection connection()throws Exception{return DriverManager.getConnection(URL,USER,password);}
    static boolean runMigratorSql(String file,List<String> vars){var args=new ArrayList<>(List.of("-X","-w","-h","127.0.0.1","-p","55432","-U","eventflow_migrator","-d",DB,"-v","ON_ERROR_STOP=1"));args.addAll(vars);args.addAll(List.of("-f",file));return BackupRestoreCheck.tool("psql",args,Map.of("PGPASSWORD",migratorPassword,"PGSSLMODE","disable"));}
    static Flyway flyway(String target){return Flyway.configure().dataSource(URL,safe?"eventflow_migrator":USER,safe?migratorPassword:password).locations("filesystem:src/main/resources/db/"+(safe?"migration-safe":"migration")).baselineOnMigrate(false).cleanDisabled(true).target(target).validateOnMigrate(true).load();}
    static void require(boolean ok){if(!ok)throw new IllegalStateException();}
    static int[] copyTestReports(Path destination)throws Exception {
        Files.createDirectories(destination);int[] totals=new int[4];Path source=Path.of("build/test-results/test");
        try(var reports=Files.list(source)) {
            for(Path report:reports.filter(p->p.getFileName().toString().startsWith("TEST-")&&p.toString().endsWith(".xml")).toList()) {
                Files.copy(report,destination.resolve(report.getFileName()),StandardCopyOption.REPLACE_EXISTING);
                String xml=Files.readString(report);var suite=java.util.regex.Pattern.compile("<testsuite\\b[^>]*>").matcher(xml);
                if(!suite.find())continue;String tag=suite.group();
                for(int i=0;i<4;i++){String key=new String[]{"tests","failures","errors","skipped"}[i];var value=java.util.regex.Pattern.compile("\\b"+key+"=\"(\\d+)\"").matcher(tag);if(value.find())totals[i]+=Integer.parseInt(value.group(1));}
            }
        }
        return totals;
    }
    static String originalFingerprint(Connection db,List<String> ids)throws Exception {
        var hash=MessageDigest.getInstance("SHA-256");
        try(var p=db.prepareStatement("SELECT row_to_json(t)::text FROM (SELECT user_id,user_name,user_email,user_phone_prefix_id,user_phone_number,user_password_hash,user_pfp_url,user_birth_date,user_nationality_country_code FROM users WHERE user_id=ANY(?) ORDER BY user_id) t")){
            p.setArray(1,db.createArrayOf("uuid",ids.toArray()));try(var r=p.executeQuery()){while(r.next()){hash.update(r.getString(1).getBytes(StandardCharsets.UTF_8));hash.update((byte)10);}}
        }
        return HexFormat.of().formatHex(hash.digest());
    }
}
