# PostgreSQL 18: configure a dedicated EventFlow database

Confirmed: PostgreSQL 18.6 accepts connections on localhost:5432. The passwordless
read-only query as postgres failed with `fe_sendauth: no password supplied`.
The existence and contents of eventflow_db remain unknown. No SQL mutations or
migrations were run. Do not use postgres as the application account.

## 1. Open an interactive administrator connection

Open **SQL Shell (psql)** from the Windows Start menu and accept:

```text
Server: localhost
Database: postgres
Port: 5432
Username: postgres
```

Enter the installed postgres password only in that local password prompt. Input
is hidden. Alternatively, from PowerShell:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -h localhost -p 5432 -U postgres -d postgres -W
```

If the password is unknown, recover it through the installation administrator;
do not change authentication to trust or guess passwords.

## 2. Inspect before creating anything

Run inside psql:

```sql
\set ON_ERROR_STOP on
SELECT datname, pg_get_userbyid(datdba) AS owner
FROM pg_database WHERE datname = 'eventflow_db';
SELECT rolname, rolcanlogin, rolsuper, rolcreatedb, rolcreaterole
FROM pg_roles WHERE rolname = 'eventflow_app';
```

If eventflow_db exists, do not execute the creation block below. Inspect it:

```sql
\connect eventflow_db postgres
SELECT schemaname, tablename FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY schemaname, tablename;
SELECT to_regclass('public.flyway_schema_history');
```

Only if the last query returns a relation, inspect its history:

```sql
SELECT installed_rank, version, description, success
FROM public.flyway_schema_history ORDER BY installed_rank;
```

Keep existing roles, ownership, tables and migration history intact. Existing
database permissions need review before connecting the application.

## 3. Only if eventflow_db does not exist

Run the CREATE ROLE and password commands only if eventflow_app also does not
exist. If the role already exists, do not replace it, reset its password or change
its permissions; confirm it is the authorized application role first.

```sql
CREATE ROLE eventflow_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
\password eventflow_app
```

The password command prompts twice without including the password in SQL history.
Then, only for the absent database and authorized dedicated role:

```sql
CREATE DATABASE eventflow_db OWNER eventflow_app TEMPLATE template0 ENCODING 'UTF8';
\connect eventflow_db postgres
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO eventflow_app;
\quit
```

Database ownership provides the local migration role with the DDL privileges needed
for Flyway. It does not grant cluster administration. Do not grant SUPERUSER,
CREATEDB or CREATEROLE. Separate migration/runtime roles can be used for deployment.

## 4. Test the application connection without running Spring or Flyway

In PowerShell:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -h localhost -p 5432 -U eventflow_app -d eventflow_db -W -c 'SELECT current_user, current_database(), current_schema();'
```

Enter the application password at the local prompt. The expected user/database
are eventflow_app and eventflow_db. No migrations are executed by this command.

## 5. Prepare the ignored local configuration

From the repository root, copy the template only if .env is absent:

```powershell
if (-not (Test-Path -LiteralPath .env)) { Copy-Item -LiteralPath .env.example -Destination .env }
notepad .env
```

Replace DB_PASSWORD locally. Do not use shell-style surrounding quotes. This file
is parsed as Java properties: a literal backslash requires `\\`. Keep
SPRING_PROFILES_ACTIVE=local. Generate and save JWT_SECRET without displaying it:

```powershell
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$jwtKey = [Convert]::ToBase64String($jwtBytes)
$envFile = (Resolve-Path -LiteralPath .env).Path
$envText = [System.IO.File]::ReadAllText($envFile)
if ($envText -notmatch '(?m)^JWT_SECRET=REPLACE_WITH_RANDOM_BASE64_KEY\r?$') {
    throw 'JWT_SECRET is not the template placeholder; leave the existing key unchanged.'
}
$envText = [regex]::Replace($envText, '(?m)^JWT_SECRET=REPLACE_WITH_RANDOM_BASE64_KEY\r?$', "JWT_SECRET=$jwtKey")
[System.IO.File]::WriteAllText($envFile, $envText, [System.Text.UTF8Encoding]::new($false))
Remove-Variable jwtKey, jwtBytes, envText
git check-ignore .env
```

The JWT command deliberately refuses to rotate an existing key. Never print or
commit .env. Spring Boot already imports it using
`optional:file:.env[.properties]`; no dotenv library or shell sourcing is needed.

Stop here until the existing database contents and Flyway history are confirmed.
The local profile disables migrations and automatic baselining. Starting the API
against an empty database now would fail schema validation; that is expected and
does not authorize enabling migrations blindly.
