# EventFlow API

Backend modular de EventFlow basado en Spring Boot 4, Kotlin, PostgreSQL, Flyway y JWT. Implementa el alcance del DERCAS v2.0: identidad, marketplace, eventos configurables, invitaciones, ejecución y módulos especializados reutilizables.

## Requisitos

- Java 25
- PostgreSQL 15+
- Una clave JWT Base64 de al menos 32 bytes

```bash
cp .env.example .env
openssl rand -base64 32
./gradlew bootRun
```

La API queda en `http://localhost:5080/api` y Swagger en `http://localhost:5080/swagger-ui`.

## Configuración externa

- Correo: variables estándar `SPRING_MAIL_*`. Si no existe proveedor, la cuenta y el token se crean, pero no se intenta un envío.
- Google: `GOOGLE_CLIENT_ID`. El backend valida el ID token directamente contra Google y comprueba audiencia y correo verificado.
- Archivos: `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` y `SUPABASE_BUCKET`. Los objetos nunca exponen la service key; se descargan mediante un endpoint autenticado que valida acceso al evento.
- Administración inicial: registra y verifica una cuenta, define su correo en `BOOTSTRAP_ADMIN_EMAIL` y reinicia una vez para asignarle `ADMIN`.
- `AUTH_EXPOSE_TOKENS=true` devuelve tokens de verificación/recuperación únicamente para desarrollo y pruebas. Debe permanecer desactivado en producción.

## Contratos principales

### Autenticación y perfil

- `POST /api/auth/register`, `/login`, `/google`, `/refresh`
- `POST /api/auth/verify-email`, `/resend-verification`
- `POST /api/auth/forgot-password`, `/reset-password`, `/change-password`
- `POST /api/auth/logout`, `/logout-all`, `/deactivate`, `/request-deletion`, `/cancel-deletion`
- `POST /api/auth/request-email-change`, `/confirm-email-change`
- `GET/PATCH /api/profile`; preferencias y solicitudes de rol bajo `/api/profile/*`

El login recibe `{ "identifier": "correo o teléfono", "password": "..." }`. Los refresh tokens rotan al usarse y las sesiones pueden revocarse individual o globalmente.

### Eventos y configuración modular

- CRUD y transiciones: `/api/events`
- Panel: `/api/events/{eventId}/dashboard`
- Catálogo: `/api/modules/catalog`
- Configuración: `/api/events/{eventId}/modules/{code}`
- Navegación de invitado: `/api/events/{eventId}/modules/navigation`

Los tipos de evento aplican las plantillas sugeridas del DERCAS; el organizador puede reordenar, destacar, habilitar y quitar módulos. Se validan dependencias y desactivaciones incompatibles.

### Marketplace y ejecución

- Espacios/servicios: `/api/offerings`
- Disponibilidad: `/api/offerings/{id}/availability`
- Reservaciones, decisiones, cancelación, pago simulado y reseña: `/api/reservations/*`
- Invitaciones, RSVP, regeneración, Event Pass, check-in/out: `/api/events/{id}/invitations` y `/api/invitations/access/{token}`
- Agenda, Ahora/Siguiente y favoritos: `/api/events/{id}/agenda`
- Asistencia priorizada: `/api/events/{id}/assistance`
- Avisos segmentados y mensajería: `/api/events/{id}/notifications`, `/messages`
- Archivos privados: `/api/events/{id}/files`
- Administración: `/api/admin/*`; reportes en `/api/reports`

### Datos de módulos especializados

Los módulos reutilizan un motor común con aislamiento por evento, control de capacidad, bloqueo pesimista, acciones únicas y conservación histórica:

```text
POST /api/events/{eventId}/module-data/{module}/{type}
GET  /api/events/{eventId}/module-data/{module}/{type}
PATCH/DELETE /api/events/{eventId}/module-data/records/{recordId}
POST /api/events/{eventId}/module-data/records/{recordId}/actions
```

Tipos admitidos:

| Módulo | Tipos |
|---|---|
| MAP | `ZONE`, `POINT` |
| ORD | `MENU_CATEGORY`, `MENU_ITEM`, `ORDER` |
| QUE / BKG | `QUEUE` / `ACTIVITY` |
| INT | `POLL`, `QUESTION_BOARD`, `TRIVIA`, `DRAW`, `GUEST_MESSAGE`, `SONG` |
| GAM | `PASSPORT`, `MILESTONE`, `MISSION`, `BADGE` |
| GAL | `PHOTO` |
| NET | `PROFILE`, `MEETING` |
| EXH / SES | `EXHIBITOR`, `STAND` / `SPEAKER`, `SESSION` |
| SPT | `PARTICIPANT`, `TEAM`, `MATCH`, `BRACKET` |
| TRN | `ROUTE`, `DEPARTURE` |
| LNF / AFO | `LOST_ITEM`, `FOUND_ITEM` / `ZONE_CAPACITY`, `SERVICE_STATUS` |
| RSC / REV | `RESOURCE`, `CERTIFICATE` / `SURVEY`, `SURVEY_RESPONSE` |

Las acciones (`JOIN`, `RESERVE`, `VOTE`, `SAVE`, `CHECK_IN`, `CANCEL`, etc.) guardan actor, fecha y payload. `unique=true` evita duplicados por usuario; las acciones que ocupan/liberan cupo actualizan el contador de forma transaccional.

## Seguridad y reglas aplicadas

- JWT stateless enlazado a una sesión persistida y revocable.
- Roles, endpoints administrativos y pertenencia por evento.
- Tokens aleatorios almacenados únicamente como SHA-256.
- Separación estricta por `event_id` en toda consulta operativa.
- Límites de cupo, check-in idempotente, política de reingreso y conflictos de reservación.
- Errores JSON uniformes sin trazas internas.
- Auditoría para cambios operativos y administrativos.
- Eliminación diferida con anonimización programada.

## Pruebas

```bash
./gradlew test
```

Las pruebas usan H2 en modo PostgreSQL para cargar el contexto y validar el mapeo ORM sin depender de una base externa. En despliegue, Flyway ejecuta las migraciones PostgreSQL de `src/main/resources/db/migration`.
