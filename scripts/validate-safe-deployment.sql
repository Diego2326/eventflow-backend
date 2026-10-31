-- Read-only postflight before traffic or authorized administrative provisioning.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT version,script,checksum,success FROM public.flyway_schema_history ORDER BY installed_rank;
DO $$
DECLARE role_name text; item record;
BEGIN
 IF (SELECT count(*) FROM public.flyway_schema_history)<>27 OR EXISTS(SELECT FROM public.flyway_schema_history WHERE NOT success) THEN RAISE EXCEPTION 'Incomplete migration history'; END IF;
 IF NOT EXISTS(SELECT FROM public.flyway_schema_history WHERE version='8' AND checksum=1426551133)
 OR NOT EXISTS(SELECT FROM public.flyway_schema_history WHERE version='9' AND checksum=-58182200) THEN RAISE EXCEPTION 'Not the safe V8/V9 line'; END IF;
 IF NOT EXISTS(SELECT FROM public.flyway_schema_history WHERE version='22' AND checksum=-1549629595) THEN RAISE EXCEPTION 'Missing safe Expo device migration'; END IF;
 IF NOT EXISTS(SELECT FROM public.flyway_schema_history WHERE version='23' AND checksum=-996366283) THEN RAISE EXCEPTION 'Missing safe notification read migration'; END IF;
 IF NOT EXISTS(SELECT FROM public.flyway_schema_history WHERE version='24' AND checksum=-2076696153) OR NOT EXISTS(SELECT FROM public.flyway_schema_history WHERE version='25' AND checksum=8311463) OR NOT EXISTS(SELECT FROM public.flyway_schema_history WHERE version='26' AND checksum=-1906531511) THEN RAISE EXCEPTION 'Missing EventFlow REST migrations'; END IF;
 IF NOT EXISTS(SELECT FROM public.flyway_schema_history WHERE version='27' AND checksum=46441340)
 OR NOT EXISTS(SELECT FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='user_terms_accepted_at') THEN RAISE EXCEPTION 'Missing Google terms acceptance migration'; END IF;
 IF EXISTS(SELECT FROM public.user_role WHERE role='ADMIN') THEN RAISE EXCEPTION 'Unexpected ADMIN before explicit provisioning'; END IF;
 IF EXISTS(SELECT FROM public.users WHERE user_id IN ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333') OR lower(user_email) IN ('demo.organizador@eventflow.gt','demo.invitada@eventflow.gt','demo.proveedor@eventflow.gt')) THEN RAISE EXCEPTION 'Demo account detected'; END IF;
 IF has_table_privilege('eventflow_runtime','public.flyway_schema_history','SELECT,INSERT,UPDATE,DELETE,TRUNCATE') OR has_schema_privilege('eventflow_runtime','public','CREATE') OR has_database_privilege('eventflow_runtime',current_database(),'CREATE') THEN RAISE EXCEPTION 'Runtime administrative access'; END IF;
 IF EXISTS(SELECT FROM pg_roles WHERE rolname='eventflow_runtime' AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls)) THEN RAISE EXCEPTION 'Unsafe runtime attributes'; END IF;
 IF NOT has_table_privilege('eventflow_runtime','public.push_device','SELECT,INSERT,UPDATE,DELETE') THEN RAISE EXCEPTION 'Runtime lacks push device DML'; END IF;
 IF NOT has_table_privilege('eventflow_runtime','public.notification_read','SELECT,INSERT,UPDATE,DELETE') THEN RAISE EXCEPTION 'Runtime lacks notification receipt DML'; END IF;
 IF NOT has_table_privilege('eventflow_runtime','public.event_template','SELECT,INSERT,UPDATE,DELETE') OR NOT has_table_privilege('eventflow_runtime','public.user_privacy_preference','SELECT,INSERT,UPDATE,DELETE') OR NOT has_table_privilege('eventflow_runtime','public.message_read_receipt','SELECT,INSERT,UPDATE,DELETE') THEN RAISE EXCEPTION 'Runtime lacks new REST feature DML'; END IF;
 FOR role_name IN SELECT unnest(ARRAY['anon','authenticated']) LOOP
  FOR item IN SELECT c.oid FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p') LOOP
   IF has_table_privilege(role_name,item.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN') OR has_any_column_privilege(role_name,item.oid,'SELECT,INSERT,UPDATE,REFERENCES') THEN RAISE EXCEPTION 'Data API effective table/column access remains'; END IF;
  END LOOP;
  IF EXISTS(SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND CASE WHEN c.relkind='S' THEN has_sequence_privilege(role_name,c.oid,'SELECT') OR has_sequence_privilege(role_name,c.oid,'USAGE') OR has_sequence_privilege(role_name,c.oid,'UPDATE') ELSE false END) THEN RAISE EXCEPTION 'Data API sequence access remains'; END IF;
 END LOOP;
END $$;
SELECT count(*) AS users,count(*) FILTER(WHERE user_status='PENDING_VERIFICATION') AS pending,count(*) FILTER(WHERE user_email_verified_at IS NOT NULL) AS verified FROM public.users;
SELECT count(*) AS role_assignments FROM public.user_role;
SELECT rolname,rolsuper,rolcreatedb,rolcreaterole,rolinherit,rolbypassrls FROM pg_roles WHERE rolname IN ('eventflow_runtime','eventflow_migrator');
SELECT has_database_privilege('eventflow_runtime',current_database(),'TEMP') AS runtime_public_temp_inheritance;
COMMIT;
