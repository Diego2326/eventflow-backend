\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT current_database(), current_user, current_schema(), current_setting('search_path') AS search_path;
SELECT version, description, type, script, checksum, success
FROM public.flyway_schema_history ORDER BY installed_rank;

WITH expected(version, script, description, checksum) AS (VALUES
 ('1','V1__CreatePhonePrefixTable.sql','CreatePhonePrefixTable',1915841373),
 ('2','V2__SeedPhonePrefix.sql','SeedPhonePrefix',198510625),
 ('3','V3__UserTableCreate.sql','UserTableCreate',-1566007137),
 ('4','V4__add_birth_date_and_nationality_to_users.sql','add birth date and nationality to users',1955617113)
)
SELECT e.version, e.script AS local_script, h.script AS database_script,
       e.checksum AS local_checksum, h.checksum AS database_checksum,
       CASE WHEN h.success AND h.type='SQL' AND h.script=e.script
            AND h.description=e.description AND h.checksum=e.checksum THEN 'MATCH'
            ELSE 'STOP: MISMATCH OR MISSING' END AS compatibility
FROM expected e LEFT JOIN public.flyway_schema_history h USING(version)
ORDER BY e.version;

SELECT table_schema, table_name FROM information_schema.tables
WHERE table_schema='public' ORDER BY table_name;
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns WHERE table_schema='public' AND table_name='users'
ORDER BY ordinal_position;
SELECT count(*) AS existing_users FROM public.users;
SELECT count(*) AS duplicate_phone_groups FROM (
 SELECT user_phone_prefix_id, user_phone_number FROM public.users
 WHERE user_phone_number IS NOT NULL
 GROUP BY user_phone_prefix_id, user_phone_number
 HAVING count(*)>1 AND user_phone_prefix_id IS NOT NULL
) duplicates;
SELECT count(*) AS conflicting_demo_users FROM public.users
WHERE user_id IN ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333')
 OR lower(user_email) IN ('demo.organizador@eventflow.gt','demo.invitada@eventflow.gt','demo.proveedor@eventflow.gt');
SELECT name, default_version, installed_version FROM pg_available_extensions WHERE name='btree_gist';
SELECT e.extname, n.nspname AS extension_schema FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace WHERE e.extname='btree_gist';
SELECT has_schema_privilege(current_user,'public','CREATE') AS can_create_public,
       has_database_privilege(current_user,current_database(),'CREATE') AS can_create_database_objects;
-- Ownership of existing application tables is also required for ALTER TABLE.
SELECT tablename, tableowner FROM pg_tables WHERE schemaname='public';
COMMIT;
