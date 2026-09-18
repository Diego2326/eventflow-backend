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
dotnet tool restore
dotnet ef database update --project src/Infrastructure --startup-project src/Api
ASPNETCORE_ENVIRONMENT=Development dotnet run --project src/Api --urls http://localhost:5080
```

Configura la conexión mediante variables de entorno. Swagger se publica en `/swagger`.

