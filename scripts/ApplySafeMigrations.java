import java.util.*;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;

/** Standalone operator tool; never starts server/jobs. Default mode only checks. */
class ApplySafeMigrations {
    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        try {
            var options=Arrays.asList(args);
            boolean apply=options.contains("--apply-v5-v26"), after=options.contains("--validate-after");
            if(apply && (!options.contains("--approved") || !"yes".equals(System.getenv("EVENTFLOW_BACKUP_CONFIRMED"))))
                throw new IllegalStateException("Explicit approval and confirmed backup required");
            var env=new StandardEnvironment();env.setActiveProfiles("local");ConfigDataEnvironmentPostProcessor.applyTo(env);
            String url=env.getRequiredProperty("DB_URL");
            String user=env.getRequiredProperty("DB_MIGRATOR_USERNAME"),pass=env.getRequiredProperty("DB_MIGRATOR_PASSWORD");
            if(!url.startsWith("jdbc:postgresql:") || url.matches("(?is).*sslmode=(disable|allow|prefer).*"))throw new IllegalStateException();
            // Enforce TLS without printing or rewriting the operator's credentials.
            var ds=new org.postgresql.ds.PGSimpleDataSource();ds.setUrl(url);ds.setUser(user);ds.setPassword(pass);
            String sslMode=org.postgresql.Driver.parseURL(url,new Properties()).getProperty("sslmode","require");
            ds.setSslMode(sslMode.equals("verify-full")||sslMode.equals("verify-ca")?sslMode:"require");
            try(var db=ds.getConnection();var st=db.createStatement();var rs=st.executeQuery("SELECT current_user,(SELECT count(*) FROM public.flyway_schema_history),(SELECT count(*) FROM public.flyway_schema_history WHERE NOT success)")) {
                rs.next();if(!"eventflow_migrator".equals(rs.getString(1))||rs.getInt(3)!=0||(!after&&rs.getInt(2)!=4)||(after&&rs.getInt(2)!=26))throw new IllegalStateException();
            }
            String location="filesystem:src/main/resources/db/migration-safe";
            var history=Flyway.configure().dataSource(ds).locations(location).baselineOnMigrate(false).cleanDisabled(true).target(after?"26":"4").load();
            history.validate();System.out.println("historical_checksums=VALID; standalone=true; seeds=SAFE_VARIANT");
            var flyway=Flyway.configure().dataSource(ds).locations(location).baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true).target("26").load();
            if(apply){var result=flyway.migrate();System.out.println("applied_count="+result.migrationsExecuted+" success="+result.success);}
            if(after||apply)flyway.validate();
            for(var migration:flyway.info().all())System.out.println("V"+migration.getVersion()+" state="+migration.getState()+" checksum="+migration.getChecksum());
        }catch(Exception ex){
            Throwable cause=ex;while(cause.getCause()!=null)cause=cause.getCause();
            System.out.println("STOP exception="+ex.getClass().getSimpleName()+" sqlstate="+(cause instanceof SQLException s?s.getSQLState():"n/a")+"; no secrets emitted");System.exit(1);
        }
    }
}
