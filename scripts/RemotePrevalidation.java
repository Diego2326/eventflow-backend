import java.nio.file.*;
import java.util.*;
import java.sql.*;
import java.security.*;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;

/** Remote READ ONLY audit; optionally exports a changed snapshot, never restores/migrates. */
class RemotePrevalidation {
    static String stage="configuration";
    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        try {
            var env=new StandardEnvironment();env.setActiveProfiles("local");ConfigDataEnvironmentPostProcessor.applyTo(env);
            if(!"false".equals(env.getProperty("spring.flyway.enabled")))throw new IllegalStateException();
            var profile=new StandardEnvironment();profile.setActiveProfiles("supabase");ConfigDataEnvironmentPostProcessor.applyTo(profile);
            System.out.println("supabase_locations="+profile.getProperty("spring.flyway.locations")+" flyway_enabled="+profile.getProperty("spring.flyway.enabled")+" baseline="+profile.getProperty("spring.flyway.baseline-on-migrate")+" hibernate="+profile.getProperty("spring.jpa.hibernate.ddl-auto"));
            for(String key:List.of("DB_RUNTIME_USERNAME","DB_RUNTIME_PASSWORD","DB_MIGRATOR_USERNAME","DB_MIGRATOR_PASSWORD"))System.out.println(key+"_present="+(env.getProperty(key)!=null&&!env.getProperty(key).isBlank()));
            String url=env.getRequiredProperty("DB_URL");var connectionProps=org.postgresql.Driver.parseURL(url,new Properties());
            if(connectionProps==null||url.matches("(?is).*sslmode=(disable|allow|prefer).*"))throw new IllegalStateException();
            var ds=new org.postgresql.ds.PGSimpleDataSource();ds.setUrl(url);ds.setUser(env.getRequiredProperty("DB_USERNAME"));ds.setPassword(env.getRequiredProperty("DB_PASSWORD"));ds.setSslMode(connectionProps.getProperty("sslmode","require"));ds.setConnectTimeout(15);ds.setSocketTimeout(120);
            Path evidence=Files.createDirectory(Path.of("backups","remote-prevalidation-"+System.currentTimeMillis())).toAbsolutePath();
            System.out.println("evidence_directory="+evidence);
            stage="remote_read_only";
            try(var db=ds.getConnection()) {
                db.setReadOnly(true);db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);db.setAutoCommit(false);
                var executor=db.unwrap(org.postgresql.core.BaseConnection.class).getQueryExecutor();
                var field=org.postgresql.core.QueryExecutorBase.class.getDeclaredField("pgStream");field.setAccessible(true);
                var socket=((org.postgresql.core.PGStream)field.get(executor)).getSocket();
                System.out.println("client_tls="+(socket instanceof javax.net.ssl.SSLSocket));
                if(socket instanceof javax.net.ssl.SSLSocket ssl)System.out.println("tls_protocol="+ssl.getSession().getProtocol());
                q(db,"server","SELECT current_setting('server_version') AS version,current_setting('transaction_read_only') AS read_only,current_user AS audit_role");
                q(db,"history","SELECT version,script,checksum,success FROM public.flyway_schema_history ORDER BY installed_rank");
                q(db,"extensions","SELECT e.extname,e.extversion,n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace ORDER BY extname");
                q(db,"btree_gist_available","SELECT version,trusted,superuser FROM pg_available_extension_versions WHERE name='btree_gist' AND version='1.7'");
                q(db,"roles","SELECT rolname,rolsuper,rolcreatedb,rolcreaterole,rolinherit,rolbypassrls FROM pg_roles WHERE rolname IN (current_user,'eventflow_runtime','eventflow_migrator','anon','authenticated') ORDER BY rolname");
                q(db,"dedicated_roles","SELECT to_regrole('eventflow_runtime') IS NOT NULL AS runtime_exists,to_regrole('eventflow_migrator') IS NOT NULL AS migrator_exists");
                q(db,"memberships","SELECT member.rolname AS member,parent.rolname AS inherited_role,m.admin_option,m.inherit_option,m.set_option FROM pg_auth_members m JOIN pg_roles member ON member.oid=m.member JOIN pg_roles parent ON parent.oid=m.roleid WHERE member.rolname IN (current_user,'eventflow_runtime','eventflow_migrator','anon','authenticated') ORDER BY 1,2");
                q(db,"schema_grants","SELECT n.nspname,CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END AS grantee,a.privilege_type FROM pg_namespace n CROSS JOIN LATERAL aclexplode(COALESCE(n.nspacl,acldefault('n',n.nspowner))) a WHERE n.nspname='public' ORDER BY 1,2,3");
                q(db,"owners_rls","SELECT tablename,tableowner,rowsecurity FROM pg_tables WHERE schemaname='public' ORDER BY tablename");
                q(db,"table_grants","SELECT grantee,table_name,string_agg(privilege_type,',' ORDER BY privilege_type) AS privileges FROM information_schema.role_table_grants WHERE table_schema='public' AND grantee IN ('anon','authenticated','PUBLIC') GROUP BY grantee,table_name ORDER BY grantee,table_name");
                q(db,"sequence_grants","SELECT c.relname,CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END AS grantee,a.privilege_type FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace CROSS JOIN LATERAL aclexplode(COALESCE(c.relacl,acldefault('s',c.relowner))) a WHERE n.nspname='public' AND c.relkind='S' AND (a.grantee=0 OR a.grantee IN (SELECT oid FROM pg_roles WHERE rolname IN ('anon','authenticated'))) ORDER BY 1,2,3");
                q(db,"defaults","SELECT pg_get_userbyid(d.defaclrole) AS creator,COALESCE(n.nspname,'GLOBAL') AS schema,d.defaclobjtype,CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END AS grantee,string_agg(a.privilege_type,',' ORDER BY a.privilege_type) AS privileges FROM pg_default_acl d LEFT JOIN pg_namespace n ON n.oid=d.defaclnamespace CROSS JOIN LATERAL aclexplode(d.defaclacl) a WHERE (d.defaclnamespace=0 OR n.nspname='public') AND (a.grantee=0 OR a.grantee IN (SELECT oid FROM pg_roles WHERE rolname IN ('anon','authenticated'))) GROUP BY 1,2,3,4 ORDER BY 1,2,3,4");
                q(db,"policies","SELECT count(*) FROM pg_policies WHERE schemaname='public'");
                q(db,"exposed_schemas","SELECT current_setting('pgrst.db_schemas',true) AS visible_session_setting");
                q(db,"exposed_role_settings","SELECT COALESCE(r.rolname,'ALL') AS role,s.setting FROM pg_db_role_setting s0 LEFT JOIN pg_roles r ON r.oid=s0.setrole CROSS JOIN LATERAL unnest(s0.setconfig) s(setting) WHERE s.setting LIKE 'pgrst.db_schemas=%' OR s.setting LIKE 'pgrst.db_extra_search_path=%'");
                q(db,"column_grants","SELECT count(*) FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND a.attacl IS NOT NULL");
                q(db,"dependencies","SELECT count(*) AS external_dependencies FROM pg_depend d JOIN pg_class target ON target.oid=d.refobjid JOIN pg_namespace n ON n.oid=target.relnamespace WHERE d.refclassid='pg_class'::regclass AND n.nspname='public' AND target.relname IN ('users','phone_prefix','country','flyway_schema_history') AND d.deptype='n'");
                var facts=BackupRestoreCheck.facts(db);BackupRestoreCheck.writeFacts(evidence.resolve("current-facts.properties"),facts);
                var old=new Properties();try(var in=Files.newInputStream(Path.of("backups/20261030T225844211Z/source-facts.properties"))){old.load(in);}
                var previous=new TreeMap<String,String>();for(String key:old.stringPropertyNames())previous.put(key,old.getProperty(key));
                boolean match=previous.equals(facts);
                System.out.println("backup_public_content_schema_history_sequences_match="+match);
                for(String table:BackupRestoreCheck.TABLES)System.out.println(table+" rows="+facts.get(table+".count")+" backup_rows_and_content_match="+(Objects.equals(previous.get(table+".sha256"),facts.get(table+".sha256"))&&Objects.equals(previous.get(table+".count"),facts.get(table+".count"))));
                Path backup=Path.of("backups/20261030T225844211Z");
                for(String archive:List.of("eventflow-public.dump","eventflow-database.dump")){
                    String expected=Files.readString(backup.resolve(archive+".sha256")).split("\\s+")[0];
                    boolean intact=expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(backup.resolve(archive)))));
                    System.out.println(archive+"_sha256_valid="+intact);if(!intact)throw new IllegalStateException();
                }
                if(!match) {
                    stage="updated_snapshot_export";
                    String snapshot;try(var st=db.createStatement();var r=st.executeQuery("SELECT pg_export_snapshot()")){r.next();snapshot=r.getString(1);}
                    for(boolean full:List.of(true,false)){
                        String name=full?"eventflow-database.dump":"eventflow-public.dump";Path archive=evidence.resolve(name);
                        var cmd=new ArrayList<>(List.of("-h",connectionProps.getProperty("PGHOST"),"-p",connectionProps.getProperty("PGPORT","5432"),"-U",env.getRequiredProperty("DB_USERNAME"),"-d",connectionProps.getProperty("PGDBNAME"),"-w","--format=custom","--snapshot="+snapshot,"--file="+archive));if(!full)cmd.add("--schema=public");
                        if(!BackupRestoreCheck.tool("pg_dump",cmd,Map.of("PGPASSWORD",env.getRequiredProperty("DB_PASSWORD"),"PGSSLMODE",connectionProps.getProperty("sslmode","require"))))throw new IllegalStateException();
                        if(Files.size(archive)==0||!BackupRestoreCheck.tool("pg_restore",List.of("--list",archive.toString()),Map.of())||!BackupRestoreCheck.tool("pg_restore",List.of("--file=NUL",archive.toString()),Map.of()))throw new IllegalStateException();
                        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)));
                        Files.writeString(evidence.resolve(name+".sha256"),hash+"  "+name+"\n");System.out.println("updated_backup="+name+" bytes="+Files.size(archive)+" sha256="+hash);
                    }
                    BackupRestoreCheck.writeFacts(evidence.resolve("source-facts.properties"),facts);
                }
                Files.writeString(evidence.resolve("result.txt"),"public_backup_representative="+match+"\nsource_read_only=true\nno_migrations=true\n");
                db.rollback();System.out.println("remote_transaction=READ_ONLY_ROLLBACK; migrations=NONE; restores=NONE");
            }
        }catch(Exception ex){System.out.println("STOP stage="+stage+" type="+ex.getClass().getSimpleName()+"; no exception details or secrets emitted");System.exit(1);}
    }
    static void q(Connection db,String label,String sql)throws Exception {
        try(var st=db.createStatement();var r=st.executeQuery(sql)){
            while(r.next()){var text=new StringJoiner(" ",label+": ","");for(int i=1;i<=r.getMetaData().getColumnCount();i++)text.add(r.getMetaData().getColumnLabel(i)+"="+r.getString(i));System.out.println(text);}
        }
    }
}
