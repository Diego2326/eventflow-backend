-- REVIEW ONLY. Do not run until backup, isolated rehearsal and approval.
-- psql -X -v backend_role=ROLE -v creator_role=ROLE -v apply=false -f this-file
-- Set both to the actual authorized JDBC/migration roles, never anon/authenticated.
\set ON_ERROR_STOP on
\if :{?apply}
\else
\set apply false
\endif
BEGIN;
SET LOCAL lock_timeout='5s';
SELECT set_config('eventflow.backend_role', :'backend_role', true);
SELECT set_config('eventflow.creator_role', :'creator_role', true);
CREATE TEMP TABLE ef_targets(name text PRIMARY KEY) ON COMMIT DROP;
INSERT INTO ef_targets VALUES
 ('country'),('phone_prefix'),('users'),('flyway_schema_history'),('user_role'),
 ('auth_token'),('user_session'),('notification_preference'),('event'),
 ('event_collaborator'),('module_catalog'),('module_dependency'),('event_module'),
 ('marketplace_offering'),('offering_availability'),('reservation'),('simulated_payment'),
 ('invitation'),('guest_access_log'),('agenda_item'),('agenda_favorite'),
 ('assistance_request'),('notification'),('conversation_message'),('module_record'),
 ('module_action'),('review'),('moderation_report'),('role_request'),('audit_log'),
 ('file_asset'),('survey_participation'),('ticket_type'),('ticket_order'),
 ('event_ticket'),('ticket_access_log'),('ticket_settings_audit'),('push_device'),('notification_read'),('event_template'),('user_privacy_preference'),('message_read_receipt');
CREATE TEMP TABLE ef_before ON COMMIT DROP AS
 SELECT c.oid, p.privilege,has_table_privilege(current_setting('eventflow.backend_role'),c.oid,p.privilege) AS allowed
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN ef_targets t ON t.name=c.relname
 CROSS JOIN (VALUES ('SELECT'),('INSERT'),('UPDATE'),('DELETE'),('TRUNCATE'),('REFERENCES'),('TRIGGER')) p(privilege)
 WHERE n.nspname='public' AND c.relkind IN ('r','p');
CREATE TEMP TABLE ef_sequences ON COMMIT DROP AS
 SELECT DISTINCT seq.oid,seq.relname
 FROM pg_class seq JOIN pg_namespace n ON n.oid=seq.relnamespace
 JOIN pg_depend d ON d.classid='pg_class'::regclass AND d.objid=seq.oid AND d.refclassid='pg_class'::regclass AND d.deptype IN ('a','i')
 JOIN pg_class tbl ON tbl.oid=d.refobjid JOIN ef_targets t ON t.name=tbl.relname
 WHERE n.nspname='public' AND seq.relkind='S';
CREATE TEMP TABLE ef_seq_before ON COMMIT DROP AS
 SELECT s.oid,p.privilege,has_sequence_privilege(current_setting('eventflow.backend_role'),s.oid,p.privilege) AS allowed
 FROM ef_sequences s CROSS JOIN (VALUES ('USAGE'),('SELECT'),('UPDATE')) p(privilege);
DO $$
DECLARE backend text:=current_setting('eventflow.backend_role'); item record; grantee text;
BEGIN
 IF current_setting('eventflow.creator_role') LIKE 'supabase\_%' ESCAPE '\'
    OR current_setting('eventflow.creator_role') IN ('anon','authenticated','authenticator','service_role') THEN
   RAISE EXCEPTION 'Do not alter defaults of managed/API roles';
 END IF;
 IF backend IN ('anon','authenticated','authenticator','service_role') OR NOT EXISTS(SELECT FROM pg_roles WHERE rolname=backend) THEN
   RAISE EXCEPTION 'Choose a real dedicated backend role (or existing owner for transition), not a Data API role';
 END IF;
 IF NOT has_schema_privilege(backend,'public','USAGE') THEN RAISE EXCEPTION 'Backend lacks schema USAGE'; END IF;
 FOR item IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN ef_targets t ON t.name=c.relname WHERE n.nspname='public' AND c.relkind IN ('r','p') LOOP
   EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM anon, authenticated, PUBLIC',item.relname);
   -- Explicit column grants are separate ACLs and must also be removed.
   FOR grantee IN SELECT a.attname FROM pg_attribute a WHERE a.attrelid=to_regclass(format('public.%I',item.relname)) AND a.attnum>0 AND NOT a.attisdropped LOOP
     EXECUTE format('REVOKE SELECT (%1$I), INSERT (%1$I), UPDATE (%1$I), REFERENCES (%1$I) ON TABLE public.%2$I FROM anon, authenticated, PUBLIC',grantee,item.relname);
   END LOOP;
 END LOOP;
 FOR item IN SELECT relname FROM ef_sequences LOOP
   EXECUTE format('REVOKE ALL PRIVILEGES ON SEQUENCE public.%I FROM anon, authenticated, PUBLIC',item.relname);
 END LOOP;
END $$;
-- The creator determines defaults; repeat this block for EVERY approved future creator.
-- Both global and public grants must be removed. This changes defaults, not existing objects.
ALTER DEFAULT PRIVILEGES FOR ROLE :"creator_role" REVOKE ALL PRIVILEGES ON TABLES FROM anon, authenticated, PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE :"creator_role" IN SCHEMA public REVOKE ALL PRIVILEGES ON TABLES FROM anon, authenticated, PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE :"creator_role" REVOKE ALL PRIVILEGES ON SEQUENCES FROM anon, authenticated, PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE :"creator_role" IN SCHEMA public REVOKE ALL PRIVILEGES ON SEQUENCES FROM anon, authenticated, PUBLIC;
DO $$
DECLARE item record; role_name text;
BEGIN
 IF EXISTS(SELECT FROM pg_default_acl d CROSS JOIN LATERAL aclexplode(d.defaclacl) a
   WHERE d.defaclrole=to_regrole(current_setting('eventflow.creator_role'))
     AND d.defaclobjtype IN ('r','S') AND (d.defaclnamespace=0 OR d.defaclnamespace='public'::regnamespace)
     AND (a.grantee=0 OR a.grantee IN (SELECT oid FROM pg_roles WHERE rolname IN ('anon','authenticated')))) THEN
   RAISE EXCEPTION 'Excess default table/sequence grants remain for selected creator';
 END IF;
 IF EXISTS(SELECT FROM ef_before b WHERE b.allowed AND NOT has_table_privilege(current_setting('eventflow.backend_role'),b.oid,b.privilege))
 OR EXISTS(SELECT FROM ef_seq_before b WHERE b.allowed AND NOT has_sequence_privilege(current_setting('eventflow.backend_role'),b.oid,b.privilege)) THEN
   RAISE EXCEPTION 'Backend lost effective privileges: rollback required';
 END IF;
 FOR role_name IN SELECT unnest(ARRAY['anon','authenticated']) LOOP
   FOR item IN SELECT c.oid,c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN ef_targets t ON t.name=c.relname WHERE n.nspname='public' AND c.relkind IN ('r','p') LOOP
     IF has_table_privilege(role_name,item.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')
       OR has_any_column_privilege(role_name,item.oid,'SELECT,INSERT,UPDATE,REFERENCES') THEN
       RAISE EXCEPTION 'Effective access remains for % on %, inspect memberships/ownership/PUBLIC',role_name,item.relname;
     END IF;
   END LOOP;
   IF EXISTS(SELECT FROM ef_sequences s WHERE has_sequence_privilege(role_name,s.oid,'SELECT,USAGE,UPDATE')) THEN
     RAISE EXCEPTION 'Sequence access remains for %',role_name;
   END IF;
 END LOOP;
END $$;
SELECT 'Effective table/column/sequence access denied to API roles; backend privileges preserved' AS validation;
-- Default rehearsal discards changes. apply=true is a separate, explicit approval.
\if :apply
COMMIT;
\else
ROLLBACK;
\endif
