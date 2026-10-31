# Local API startup with PostgreSQL

Use JDK 25 and the `local` profile. `application-local.properties` disables Flyway,
disables automatic baselining, and retains Hibernate schema validation. There is
no Compose file. Do not enable migrations until the target database is inspected.

PostgreSQL 18.6 is installed locally and `pg_isready -h localhost -p 5432`
reports accepting connections. An authenticated SQL connection is still required.

Copy `.env.example` to the ignored `.env` and set the authorized database values:

```properties
DB_URL=jdbc:postgresql://localhost:5432/eventflow_db
DB_USERNAME=eventflow_app
DB_PASSWORD=<database password>
JWT_SECRET=<Base64 of at least 32 cryptographically random bytes>
SPRING_PROFILES_ACTIVE=local
AUTH_EXPOSE_TOKENS=false
```

Do not paste credentials into chat or commit `.env`. The example credentials in
`.env.example` are placeholders, not verified local credentials. Spring Boot imports
`.env` through `spring.config.import=optional:file:.env[.properties]`; this is a Java
properties file, not a shell script. Do not add `export` or surrounding shell quotes.
Escape backslashes as `\\` in property values. OS environment variables and command
line properties can override file configuration. Run from the repository root.

In PowerShell, generate a JWT key directly into an environment variable without
displaying it (persist securely if sessions must survive restarts):

```powershell
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Before migrating an existing database, inspect its schemas and
`flyway_schema_history` with an authorized account. The current main configuration
disables Flyway and automatic baselining. Do not baseline an existing schema
blindly. Do not repair history, edit applied migrations,
drop schemas, or erase existing data. On a dedicated empty database, Flyway should
apply V1 through V27 after explicit migration enablement; Hibernate then validates
the schema (`ddl-auto=validate`). The local profile leaves Flyway disabled, so an
empty database will fail Hibernate validation until that separate migration step.
This has not yet been verified against the local PostgreSQL instance.

Expected endpoints after successful startup:

- API: `http://localhost:5080/api`
- Health: `http://localhost:5080/actuator/health`
- OpenAPI: `http://localhost:5080/v3/api-docs`
- Swagger: `http://localhost:5080/swagger-ui`

Android Emulator uses `http://10.0.2.2:5080/api`. Physical Android/iOS devices use
the Windows machine's LAN IP on the same network, with port 5080 allowed by its
firewall. An iOS simulator on a Mac uses the Windows host address, not its own
localhost. Expo's tunnel does not automatically expose this API. Native HTTP may
require development cleartext/ATS settings; deployed clients should use HTTPS.
These device connections have not yet been tested. Web CORS currently accepts all
origins with Bearer headers and does not enable cookie credentials.

## Production demo protection

Always select `prod` or `production` explicitly for production deployment.
`ProductionDemoAccountGuard` refuses startup when any V8 demo account is ACTIVE or
DELETION_PENDING, matching either its fixed UUID or email (including renamed seed
accounts). The V8 organizer account has ADMIN and a publicly documented password.
The guard reads data only, runs during singleton initialization before the web
server starts accepting requests, and does not rewrite any migration or user.
Disable demo accounts and revoke their sessions through an authorized operational
process before deployment. Fresh production migrations also include V8/V9, so a
fresh production database will be rejected while those demo accounts remain active.
Do not bypass the guard by changing the deployment profile.
