import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;

/** Read-only comparison against the pre-migration backup; never prints user fields. */
class VerifyOriginalUsers {
    public static void main(String[] args) {
        try {
            if(args.length!=1)throw new IllegalArgumentException();
            var saved=new Properties();try(var in=Files.newInputStream(Path.of(args[0],"source-facts.properties"))){saved.load(in);}
            var env=new StandardEnvironment();env.setActiveProfiles("local");ConfigDataEnvironmentPostProcessor.applyTo(env);
            var ds=new org.postgresql.ds.PGSimpleDataSource();String url=env.getRequiredProperty("DB_URL");
            if(!url.startsWith("jdbc:postgresql:")||url.matches("(?is).*sslmode=(disable|allow|prefer).*"))throw new IllegalStateException();
            ds.setUrl(url);ds.setUser(env.getRequiredProperty("DB_USERNAME"));ds.setPassword(env.getRequiredProperty("DB_PASSWORD"));
            String mode=org.postgresql.Driver.parseURL(url,new Properties()).getProperty("sslmode","require");ds.setSslMode(mode);
            var hash=MessageDigest.getInstance("SHA-256");int count=0;
            try(var db=ds.getConnection()){
                db.setReadOnly(true);db.setAutoCommit(false);
                try(var st=db.createStatement();var rs=st.executeQuery("SELECT row_to_json(t)::text FROM (SELECT user_id,user_name,user_email,user_phone_prefix_id,user_phone_number,user_password_hash,user_pfp_url,user_birth_date,user_nationality_country_code FROM public.users ORDER BY user_id) t")){
                    while(rs.next()){hash.update(rs.getString(1).getBytes(StandardCharsets.UTF_8));hash.update((byte)10);count++;}
                }
                db.rollback();
            }
            boolean match=Integer.toString(count).equals(saved.getProperty("users.count"))&&HexFormat.of().formatHex(hash.digest()).equals(saved.getProperty("users.sha256"));
            System.out.println("original_user_count_and_fields_preserved="+match);
            if(!match)System.exit(1);
        }catch(Exception ex){System.out.println("STOP user comparison failed; no fields or exception details emitted");System.exit(1);}
    }
}
