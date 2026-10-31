import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;

/** Dedicated backup/recovery check: no Spring context, migrations or source writes. */
class BackupRestoreCheck {
    static final Path BIN=Path.of("C:/Program Files/PostgreSQL/18/bin");
    static Path root;
    static Path cluster;
    static String stage="config";
    static int exitCode=0;
    static final List<String> TABLES=List.of("country","phone_prefix","users","flyway_schema_history");
    public static void main(String[] args) {
        boolean started=false;
        try {
            var env=new StandardEnvironment(); env.setActiveProfiles("local");
            ConfigDataEnvironmentPostProcessor.applyTo(env);
            if(!"false".equals(env.getProperty("spring.flyway.enabled"))) throw new IllegalStateException();
            String url=env.getRequiredProperty("spring.datasource.url");
            var parsed=org.postgresql.Driver.parseURL(url,new Properties());
            if(parsed==null) throw new IllegalStateException();
            var credentials=new Properties(); credentials.setProperty("user",env.getRequiredProperty("spring.datasource.username"));
            credentials.setProperty("password",env.getRequiredProperty("spring.datasource.password"));
            credentials.setProperty("sslmode","require");credentials.setProperty("readOnlyMode","always");
            credentials.setProperty("connectTimeout","15");credentials.setProperty("socketTimeout","120");
            Files.createDirectories(Path.of("backups"));
            String resume=Arrays.stream(args).filter(a->a.startsWith("--resume=")).findFirst().orElse(null);
            root=resume==null?Files.createDirectory(Path.of("backups",DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC).format(Instant.now())).toAbsolutePath()):Path.of(resume.substring(9)).toRealPath();
            if(!root.startsWith(Path.of("backups").toRealPath())) throw new IllegalStateException();
            System.out.println("backup_directory="+root);
            var pgEnv=new HashMap<String,String>();
            pgEnv.put("PGPASSWORD",credentials.getProperty("password")); pgEnv.put("PGSSLMODE","require");pgEnv.put("PGCONNECT_TIMEOUT","15");
            List<String> remote=List.of("-h",parsed.getProperty("PGHOST"),"-p",parsed.getProperty("PGPORT","5432"),"-U",credentials.getProperty("user"),"-d",parsed.getProperty("PGDBNAME"),"-w");
            Map<String,String> source;
            stage="source_snapshot";
            if(resume!=null) {
                var saved=new Properties();try(var in=Files.newInputStream(root.resolve("source-facts.properties"))){saved.load(in);}
                source=new TreeMap<>();for(String key:saved.stringPropertyNames())source.put(key,saved.getProperty(key));
                for(String archiveName:List.of("eventflow-database.dump","eventflow-public.dump")) {
                    String expected=Files.readString(root.resolve(archiveName+".sha256")).split("\\s+")[0];
                    if(!expected.equals(hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(archiveName))))))throw new IllegalStateException();
                }
                System.out.println("existing_backups_sha256=OK; source snapshot fingerprints loaded without reconnecting to Supabase");
            } else try(Connection db=DriverManager.getConnection(url,credentials)) {
                db.setReadOnly(true);db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);db.setAutoCommit(false);
                source=facts(db);
                String snapshot;
                try(var s=db.createStatement();var r=s.executeQuery("SELECT pg_export_snapshot()")){r.next();snapshot=r.getString(1);}
                for(boolean full:List.of(true,false)) {
                    stage=full?"dump_database":"dump_public";
                    Path archive=root.resolve(full?"eventflow-database.dump":"eventflow-public.dump");
                    var command=new ArrayList<>(remote);
                    command.addAll(List.of("--format=custom","--snapshot="+snapshot,"--file="+archive));
                    if(!full) command.add("--schema=public");
                    boolean ok=tool("pg_dump",command,pgEnv);
                    if(!ok) {System.out.println(stage+"=FAILED (partial archive must not be treated as backup)");if(!full)throw new IllegalStateException();continue;}
                    if(Files.size(archive)==0) throw new IllegalStateException();
                    if(!tool("pg_restore",List.of("--list",archive.toString()),Map.of())) throw new IllegalStateException();
                    if(!tool("pg_restore",List.of("--file=NUL",archive.toString()),Map.of())) throw new IllegalStateException();
                    String hash=hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)));
                    Files.writeString(root.resolve(archive.getFileName()+".sha256"),hash+"  "+archive.getFileName()+"\n");
                    System.out.println(archive.getFileName()+" bytes="+Files.size(archive)+" sha256="+hash+" integrity=OK");
                }
                db.rollback();
            }
            writeFacts(root.resolve("source-facts.properties"),source);
            stage="isolated_initdb";
            String password=Base64.getEncoder().encodeToString(SecureRandom.getSeed(36));
            Path passwordFile=root.resolve("local-password.tmp");Files.writeString(passwordFile,password,StandardCharsets.UTF_8);
            cluster=root.resolve("isolated-cluster-"+UUID.randomUUID().toString().substring(0,8));
            boolean initialized;
            try {initialized=tool("initdb",List.of("-D",cluster.toString(),"-U","eventflow_restore_owner","--auth=scram-sha-256","--pwfile="+passwordFile,"--encoding=UTF8","--locale=C"),Map.of());}
            finally{Files.deleteIfExists(passwordFile);}
            if(!initialized)throw new IllegalStateException();
            // A fresh cluster path is generated per run. Do not touch the existing Windows service.
            stage="isolated_start";
            if(!tool("pg_ctl",List.of("-D",cluster.toString(),"-l",root.resolve("isolated-server.log").toString(),"-o","-h 127.0.0.1 -p 55432 -c log_statement=none -c log_min_messages=panic -c log_min_error_statement=panic","-w","start"),Map.of()))throw new IllegalStateException();
            started=true;
            var localEnv=Map.of("PGPASSWORD",password,"PGSSLMODE","disable");
            stage="isolated_createdb";
            if(!tool("createdb",List.of("-h","127.0.0.1","-p","55432","-U","eventflow_restore_owner","-w","eventflow_restore_check"),localEnv))throw new IllegalStateException();
            var target=List.of("-h","127.0.0.1","-p","55432","-U","eventflow_restore_owner","-d","eventflow_restore_check","-w","--exit-on-error","--single-transaction","--no-owner","--no-privileges");
            boolean fullRestored=false;
            Path full=root.resolve("eventflow-database.dump");
            if(Files.exists(root.resolve("eventflow-database.dump.sha256"))) {
                stage="restore_database";
                var cmd=new ArrayList<>(target);cmd.add(full.toString());
                fullRestored=tool("pg_restore",cmd,localEnv);
                System.out.println("full_database_restore="+(fullRestored?"OK":"FAILED (transaction rolled back; see sanitized error classification)"));
            }
            if(!fullRestored) {
                stage="restore_public";
                // A failed full restore is atomic. Verify emptiness before the application-only fallback.
                try(var db=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55432/eventflow_restore_check","eventflow_restore_owner",password);var st=db.createStatement();var rs=st.executeQuery("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m')")){
                    rs.next();if(rs.getInt(1)!=0)throw new IllegalStateException();
                }
                // New databases already contain public. Omit only its CREATE SCHEMA
                // TOC entry, preserving every application object/data/constraint.
                var tocProcess=new ProcessBuilder(BIN.resolve("pg_restore.exe").toString(),"--list",root.resolve("eventflow-public.dump").toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                String toc=new String(tocProcess.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
                if(tocProcess.waitFor()!=0)throw new IllegalStateException();
                String filtered=toc.lines().filter(line->!line.matches("^\\d+; \\d+ \\d+ SCHEMA - public .*$")).reduce("",(a,b)->a+b+"\n");
                Path tocFile=root.resolve("restore-public-toc.txt");Files.writeString(tocFile,filtered);
                var cmd=new ArrayList<>(target);cmd.add("--use-list="+tocFile);cmd.add(root.resolve("eventflow-public.dump").toString());
                if(!tool("pg_restore",cmd,localEnv))throw new IllegalStateException();
                System.out.println("application_public_restore=OK");
            }
            stage="restored_validation";
            try(Connection db=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55432/eventflow_restore_check","eventflow_restore_owner",password)) {
                db.setReadOnly(true);db.setAutoCommit(false);
                var restored=facts(db);writeFacts(root.resolve("restored-facts.properties"),restored);
                boolean match=source.equals(restored);
                System.out.println("source_restored_facts_match="+match);
                for(String table:TABLES)System.out.println(table+" rows="+restored.get(table+".count")+" content_sha256_match="+Objects.equals(source.get(table+".sha256"),restored.get(table+".sha256")));
                db.rollback();if(!match)throw new IllegalStateException();
            }
            Files.writeString(root.resolve("verification-result.txt"),"application_public_restore=VERIFIED\nfull_database_restore="+fullRestored+"\nsource_read_only=true\nno_migrations=true\n");
            System.out.println("RESULT=APPLICATION_RECOVERY_VERIFIED; full_database_restore="+fullRestored);
        } catch(Exception ex) {
            System.out.println("FAILED stage="+stage+" exception="+ex.getClass().getSimpleName()+"; no exception message or secrets emitted");
            exitCode=1;
        } finally {
            if(started) {
                boolean stopped=tool("pg_ctl",List.of("-D",cluster.toString(),"-w","stop","-m","fast"),Map.of());
                System.out.println("isolated_server_stopped="+stopped);
            }
        }
        System.exit(exitCode);
    }
    static Map<String,String> facts(Connection db)throws Exception {
        var facts=new TreeMap<String,String>();
        for(String table:TABLES) {
            var hash=MessageDigest.getInstance("SHA-256");int count=0;
            String order=table.equals("flyway_schema_history")?"installed_rank":table.equals("users")?"user_id":table.equals("country")?"country_code":"phone_prefix_id";
            try(var st=db.createStatement();var rs=st.executeQuery("SELECT row_to_json(t)::text FROM public."+table+" t ORDER BY "+order)){
                while(rs.next()){hash.update(rs.getString(1).getBytes(StandardCharsets.UTF_8));hash.update((byte)10);count++;}
            }
            facts.put(table+".count",Integer.toString(count));facts.put(table+".sha256",hex(hash.digest()));
        }
        try(var st=db.createStatement();var rs=st.executeQuery("SELECT version,checksum,success FROM public.flyway_schema_history ORDER BY installed_rank")){
            while(rs.next())facts.put("flyway.V"+rs.getString(1),rs.getString(2)+":"+rs.getBoolean(3));
        }
        // Ignore owner/ACL differences intentionally requested by --no-owner/--no-privileges.
        // PostgreSQL 18 additionally represents NOT NULL as pg_constraint rows.
        // Exclude those extra catalog rows when comparing a PostgreSQL 17 backup.
        for(var entry:Map.of("constraints","SELECT c.relname,pg_get_constraintdef(k.oid) FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND k.contype <> 'n' ORDER BY c.relname,k.conname","indexes","SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY tablename,indexname","sequences","SELECT sequencename,start_value,min_value,max_value,increment_by,cycle,last_value FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename").entrySet()) {
            var hash=MessageDigest.getInstance("SHA-256");try(var st=db.createStatement();var rs=st.executeQuery(entry.getValue())){while(rs.next()){for(int i=1;i<=rs.getMetaData().getColumnCount();i++){hash.update(Objects.toString(rs.getString(i),"NULL").getBytes(StandardCharsets.UTF_8));hash.update((byte)0);}hash.update((byte)10);}}
            facts.put(entry.getKey()+".sha256",hex(hash.digest()));
        }
        return facts;
    }
    static void writeFacts(Path path,Map<String,String> facts)throws Exception {var p=new Properties();p.putAll(facts);try(var out=Files.newOutputStream(path)){p.store(out,"Counts and aggregate fingerprints only; no row values or credentials");}}
    static String hex(byte[] b){return HexFormat.of().formatHex(b);}
    static boolean tool(String name,List<String> args,Map<String,String> env) {
        try {
            var cmd=new ArrayList<String>();cmd.add(BIN.resolve(name+".exe").toString());cmd.addAll(args);
            var pb=new ProcessBuilder(cmd);pb.environment().putAll(env);pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            // Server children inherit stderr; a pipe would stay open after pg_ctl exits.
            if(name.equals("pg_ctl")) {
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                return pb.start().waitFor()==0;
            }
            var process=pb.start();String error=new String(process.getErrorStream().readAllBytes(),StandardCharsets.UTF_8);int status=process.waitFor();
            if(status!=0){
                String firstError=error.lines().filter(line->line.contains("ERROR:")).map(line->line.substring(line.indexOf("ERROR:")+6)).findFirst().orElse("").toLowerCase(Locale.ROOT);
                String diagnostic=(firstError.isBlank()?error.toLowerCase(Locale.ROOT):firstError);
                String category=diagnostic.contains("runtime lacks push device dml")?"runtime_push_privileges":diagnostic.contains("data api effective table/column access remains")?"data_api_privileges":diagnostic.contains("incomplete migration history")?"migration_history":diagnostic.contains("schema \"public\" already exists")?"public_schema_already_exists":diagnostic.contains("unrecognized privilege type")?"invalid_privilege":diagnostic.contains("extension")?"extension_dependency":diagnostic.contains("role")?"role_dependency":diagnostic.contains("permission denied")?"permission_denied":diagnostic.contains("address already in use")?"port_in_use":"tool_failure";
                var m=java.util.regex.Pattern.compile("extension [\"']([a-z0-9_]+)[\"'] (?:is not available|does not exist)").matcher(error);
                System.out.println(name+" exit="+status+" category="+category+(m.find()?" extension="+m.group(1):""));
                if(name.equals("psql")&&category.equals("tool_failure")) {
                    String hint=error.lines().filter(line->line.toLowerCase(Locale.ROOT).contains("error")).map(line->line.replaceAll("[^A-Za-z0-9 _:.()'/-]","?").trim()).findFirst().orElse("unclassified");
                    System.out.println("psql_hint="+hint.substring(0,Math.min(140,hint.length())));
                }
            }
            return status==0;
        }catch(Exception e){System.out.println(name+" launch_failed="+e.getClass().getSimpleName());return false;}
    }
}
