# EventFlow API

API REST de EventFlow construida con ASP.NET Core 10, Entity Framework Core y PostgreSQL.

## Estructura

- `src/Domain`: entidades y reglas de dominio.
- `src/Application`: contratos, DTOs y validación.
- `src/Infrastructure`: persistencia, servicios y migraciones.
- `src/Api`: controladores y configuración HTTP.
- `tests`: pruebas automatizadas.

## Ejecución

```bash
cp .env.example .env
dotnet tool restore
dotnet ef database update --project src/Infrastructure --startup-project src/Api
ASPNETCORE_ENVIRONMENT=Development dotnet run --project src/Api --urls http://localhost:5080
```

Configura la conexión mediante variables de entorno. Swagger se publica en `/swagger`.

## Contenedores para Google Cloud Run

La API y las migraciones se despliegan como imágenes independientes:

```bash
docker build -t eventflow-api -f Dockerfile .
docker build -t eventflow-migrations -f Dockerfile.migrations .

docker run --rm --env-file .env -p 8080:8080 eventflow-api
docker run --rm --env-file .env eventflow-migrations
```

`Dockerfile` escucha en el puerto `8080`, utiliza un usuario no-root y expone `/health`. Variables requeridas:

- `ConnectionStrings__Default`
- `Jwt__Key`
- `Jwt__Issuer`
- `Jwt__Audience`
- `Cors__AllowedOrigins__0` con la URL pública de la web

## Versionamiento de base de datos

Las migraciones de Entity Framework Core funcionan como Flyway: cada cambio versionado vive en `src/Infrastructure/Persistence/Migrations` y PostgreSQL registra las versiones aplicadas en `__EFMigrationsHistory`.

El proyecto `src/Migrator` es un ejecutor independiente para Cloud Run Jobs. Aplica únicamente migraciones pendientes, toma un advisory lock de PostgreSQL para impedir ejecuciones simultáneas y actualiza el catálogo inicial de roles, módulos y plantillas.

Para ambientes de demostración, el Job también puede crear las cuentas documentadas en `DEMO_USERS.md` usando `SeedDemoData__Enabled=true`.

Flujo recomendado:

1. Crear una migración con `dotnet ef migrations add Nombre --project src/Infrastructure --startup-project src/Api`.
2. Revisar y versionar los archivos generados.
3. Construir y ejecutar `Dockerfile.migrations` como Cloud Run Job.
4. Si el Job termina exitosamente, desplegar la nueva revisión de la API.

Nunca se ejecutan migraciones automáticamente al iniciar una instancia de producción.

El archivo `.env.example` contiene el inventario completo de configuración. Copia sus valores a las variables de Cloud Run y guarda `ConnectionStrings__Default`, `Jwt__Key` y `Google__ClientSecret` en Secret Manager. El archivo `.env` está ignorado por Git y excluido de ambas imágenes Docker.
