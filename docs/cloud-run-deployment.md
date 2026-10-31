# Despliegue de EventFlow API en Google Cloud Run

El backend incluye un `Dockerfile` multi-stage que compila el JAR y lo ejecuta con Java 25. Cloud Run inyecta `PORT`; la API lo usa como puerto principal y conserva `SERVER_PORT` para desarrollo local.

## Configuración requerida

Configura estas variables y secretos en el servicio de Cloud Run:

| Nombre | Uso |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` para habilitar el bloqueo de cuentas demo de producción. |
| `DB_URL` | URL JDBC de PostgreSQL con TLS. |
| `DB_USERNAME`, `DB_PASSWORD` | Usuario de ejecución de la base de datos. |
| `JWT_SECRET` | Clave aleatoria Base64 de al menos 32 bytes. |
| `AUTH_MAIL_REQUIRED` | `true` en producción. |
| `MAIL_HOST`, `MAIL_FROM`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Proveedor SMTP para verificación y recuperación de cuenta. |
| `AUTH_EXPOSE_TOKENS` | Mantener `false`. |

Si se usa el perfil seguro de base de datos definido para Supabase, activa `supabase,prod` y configura `DB_RUNTIME_USERNAME` y `DB_RUNTIME_PASSWORD`. En ese perfil la API usa las credenciales de ejecución restringidas. No uses credenciales de migración como usuario del servicio.

Flyway permanece deshabilitado al iniciar la API y Hibernate valida el esquema existente. Las migraciones se aplican como una operación separada con respaldo y preflight; los scripts seguros de este repositorio ya contemplan V1–V27.

## Integraciones opcionales

- `GOOGLE_CLIENT_ID` o `GOOGLE_CLIENT_IDS` para Google Sign-In.
- Para Supabase Storage: `APP_STORAGE_PROVIDER=supabase` (predeterminado), `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` y `SUPABASE_BUCKET`.
- Para Neon Object Storage: `APP_STORAGE_PROVIDER=neon`, `APP_STORAGE_BUCKET=uploads`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_ENDPOINT_URL_S3` y `AWS_REGION`.
- `EXPO_PUSH_ACCESS_TOKEN` para entrega push autenticada de Expo.
- `WEBSOCKET_ALLOWED_ORIGINS` con los orígenes HTTPS permitidos si se conecta un cliente web.
- `FRONTEND_URL` para enlaces de verificación y recuperación.

Guarda contraseñas, claves y tokens en Secret Manager y enlázalos al servicio como variables de entorno. No los incluyas en la imagen ni en el repositorio.

Para la galería, crea un bucket privado en el proveedor seleccionado. Supabase usa `eventflow` como bucket predeterminado; Neon usa el valor de `APP_STORAGE_BUCKET` (por ejemplo `uploads`). En Neon configura el endpoint S3 y las credenciales de la misma rama, más acceso path-style. Las subidas y descargas pasan por la API y se autorizan por invitación, módulo, moderación y propietario; la app no necesita acceso directo al bucket. Si faltan variables, el resto del servicio puede funcionar, pero las cargas no.

## URLs de comprobación

- Salud: `/actuator/health`
- OpenAPI: `/v3/api-docs`
- Swagger UI: `/swagger-ui/index.html`

La app móvil debe definir `EXPO_PUBLIC_DEMO_MODE=false` y `EXPO_PUBLIC_API_URL=https://<servicio>.run.app/api` en su configuración de compilación.
