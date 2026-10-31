-- APPROVAL REQUIRED. Run as the existing owner; no managed role is altered.
-- psql -X -W ... -v database_name=postgres -f scripts/bootstrap-eventflow-roles.sql
\set ON_ERROR_STOP on
BEGIN;
CREATE ROLE eventflow_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
CREATE ROLE eventflow_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
GRANT CONNECT, CREATE ON DATABASE :"database_name" TO eventflow_migrator;
GRANT CONNECT ON DATABASE :"database_name" TO eventflow_runtime;
GRANT USAGE, CREATE ON SCHEMA public TO eventflow_migrator;
GRANT USAGE ON SCHEMA public TO eventflow_runtime;
-- The operator needs SET membership to transfer ownership on PostgreSQL 17.
-- This is a dedicated-role membership, not ALTER ROLE of Supabase-managed roles.
GRANT eventflow_migrator TO CURRENT_USER WITH SET TRUE;
-- Only the four application-owned historical tables; never REASSIGN OWNED globally.
ALTER TABLE public.country OWNER TO eventflow_migrator;
ALTER TABLE public.phone_prefix OWNER TO eventflow_migrator;
ALTER TABLE public.users OWNER TO eventflow_migrator;
ALTER TABLE public.flyway_schema_history OWNER TO eventflow_migrator;
-- Password prompts are local, not plaintext SQL or arguments. Existing roles cause
-- CREATE ROLE to fail and rollback rather than silently replacing their attributes.
\password eventflow_migrator
\password eventflow_runtime
COMMIT;
