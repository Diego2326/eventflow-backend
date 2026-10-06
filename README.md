# EventFlow API

Backend modular de EventFlow basado en Spring Boot 4, Kotlin, PostgreSQL, Flyway y JWT. Cubre el ciclo base del DERCAS v2.0: identidad, marketplace, eventos configurables, invitaciones, ejecución y un motor de datos para módulos especializados.

## Arquitectura

La API se organiza primero por módulo (`auth`, `event`, `invitation`, `marketplace`, `agenda`, `assistance`, `notification`, `messaging`, `module`, `storage`, `admin`, `user`, `capacity`, `interaction`, `networking`, `transport`, `resource`, `session`, `sport`, `gamification` y `exhibition`). Cada módulo contiene las capas que necesita:

- `domain`: estados, reglas del ciclo de vida y modelos del negocio.
- `application`: servicios y casos de uso por función, contratos de entrada y puertos para persistencia y servicios externos.
- `infrastructure`: controladores HTTP, repositorios Spring Data, adaptadores de correo, Google, JWT, contraseñas, JSON y almacenamiento, además de seguridad y configuración.

Por ejemplo, `event/domain`, `event/application` y `event/infrastructure` reúnen todo lo relativo a eventos. `shared` contiene únicamente contratos y adaptadores transversales. Las migraciones de Flyway permanecen en `src/main/resources/db/migration` porque forman una secuencia única de base de datos.

Los controladores dependen de la aplicación. Los servicios de aplicación dependen de puertos y no importan infraestructura, HTTP, Spring Data ni Jackson. Los repositorios Spring Data implementan los puertos de persistencia. Las reglas de creación y transición de eventos viven en casos de uso independientes de Spring y JPA. `ArchitectureBoundaryTest` comprueba estas fronteras.

Las entidades persistentes siguen compartidas con el dominio y anotadas con JPA; los servicios transaccionales siguen usando anotaciones Spring. Esta es una adaptación pragmática que conserva el mapeo, el seguimiento de cambios de Hibernate y los contratos actuales de la API.

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

La guía de integración del frontend está organizada por módulo en [docs/frontend/README.md](docs/frontend/README.md).

## Configuración externa

- Correo: variables estándar `SPRING_MAIL_*`. Si no existe proveedor, la cuenta y el token se crean, pero no se intenta un envío.
- Google: `GOOGLE_CLIENT_ID`. El backend valida el ID token directamente contra Google y comprueba audiencia y correo verificado.
- Archivos: `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` y `SUPABASE_BUCKET`. Los objetos nunca exponen la service key; se descargan mediante un endpoint autenticado que valida acceso al evento.
- Administración inicial: registra y verifica una cuenta, define su correo en `BOOTSTRAP_ADMIN_EMAIL` y reinicia una vez para asignarle `ADMIN`.
- Encuestas anónimas: configura una clave estable en `SURVEY_ANONYMITY_KEY`. Si se omite, se usa `JWT_SECRET`. Cambiarla impide reconocer respuestas previas en encuestas aún activas.
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
- Asignación de mesa, asiento y sector: `PATCH /api/events/{id}/invitations/{invitationId}/seat`; si existe `GST:SEATING_AREA` con el nombre de la mesa, se respeta su capacidad.
- Agenda, Ahora/Siguiente y favoritos: `/api/events/{id}/agenda`
- Agenda personal: `GET /api/events/{id}/agenda/mine`
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
| INT | `POLL`, `QUESTION_BOARD`, `QUESTION`, `TRIVIA`, `DRAW`, `GUEST_MESSAGE`, `SONG` |
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
- Límites de cupo, historial de check-in y check-out, política de reingreso y conflictos de reservación.
- Errores JSON uniformes sin trazas internas.
- Auditoría para cambios operativos y administrativos.
- Eliminación diferida con anonimización programada.

## Pruebas

```bash
./gradlew test
```

## Cobertura del DERCAS

La primera etapa cubre autenticación, usuarios, eventos, catálogo modular, marketplace, reservaciones, agenda, invitaciones, Event Pass, asistencia operativa, notificaciones y administración. La segunda etapa incluye pago simulado, mensajería, mapa, pedidos, interacción, galería, recursos y retroalimentación. `ORD` valida artículos, cantidades y cupo, calcula el total, controla las transiciones del pedido y avisa al invitado si `NOT` está habilitado. `INT:POLL` valida opciones, aplica la regla de voto único o múltiple y oculta resultados hasta su publicación. `INT:QUESTION` usa un tablero, moderación opcional y voto único. `MAP` oculta zonas no publicadas y permite buscar puntos, stands, sesiones y servicios, además de localizar la mesa del invitado. `REV:SURVEY_RESPONSE` exige evento finalizado y limita a una respuesta por invitado y encuesta. `CAL` genera recordatorios en la app hasta 15 minutos antes de las actividades favoritas cuando `NOT` está habilitado.

Las extensiones tienen avances verificables: `QUE` aplica cupo, turno y aviso deduplicado; `BKG` y `TRN` controlan cupos y cancelaciones; `TRN` genera avisos de salida; `INT:TRIVIA` puntúa respuestas, `INT:DRAW` sortea entre invitados con check-in y excluye ganadores anteriores, y el libro de mensajes usa moderación configurable. `NET` limita los perfiles al consentimiento, ofrece sugerencias por intereses, QR de perfil y reuniones con control de choques. `GAM` comprueba hitos, calcula misiones y otorga insignias. `SPT` avanza ganadores en el bracket. `AFO` informa aforo y estado de servicios. `RSC`, `SES` y `EXH` permiten guardar y consultar recursos personales; `RSC` publica certificados privados tras verificar asistencia. `SES` registra asistencia mediante personal. `GAL` distingue galería oficial posterior. `REV` admite encuestas anónimas con respuesta única; `NOT` segmenta también por asistencia a sesión o reserva de transporte.

Las asociaciones de categoría/artículo, expositor/stand/recurso, sesión/ponente/material y ruta/salida se validan antes de publicar y al retirar registros. Los recursos requieren archivo público del evento o enlace HTTPS. Las acciones que completan misiones otorgan insignias automáticamente. Las API de Event Pass, networking y recursos entregan el valor codificable como QR; la aplicación cliente dibuja el QR y muestra la vista de impresión.

Las sesiones y los partidos se vinculan mediante `payload.agendaItemId` a una actividad `CAL` del mismo evento. Los cambios de programación se hacen en la agenda y se reflejan en el registro vinculado. Los participantes deportivos y los partidos declaran categoría; `AFO:ZONE_CAPACITY` apunta mediante `payload.zoneId` a una zona `MAP` o mesa `GST` activa.

Para crear un pedido en `ORD:ORDER`, el payload contiene `items` con `itemId` y `quantity`, por ejemplo `{ "items": [{ "itemId": "<uuid>", "quantity": 2 }], "location": "Mesa 8" }`. Cada `ORD:MENU_ITEM` requiere `parentRecordId` de una categoría activa, `price` no negativo y puede declarar `available`; su `capacity` representa existencias. El personal con permiso `ORDERS` avanza el pedido mediante `PATCH /api/events/{eventId}/module-data/records/{recordId}` y el invitado puede cancelarlo mientras esté pendiente.

Una encuesta `INT:POLL` recibe `options` como lista de objetos `{ "id": "a", "label": "Opción A" }`, `allowMultiple` y `resultsPublished`. Cada voto usa una acción `VOTE` con `{ "optionId": "a" }`. `GET /api/events/{eventId}/module-data/records/{recordId}/poll-results` entrega el conteo al organizador y al invitado después de publicar resultados.

Un tablero `INT:QUESTION_BOARD` define `moderationRequired` y `allowVotes`. El invitado crea `INT:QUESTION` con `parentRecordId` del tablero; el organizador aprueba mediante `PATCH` con `status: "ACTIVE"`.

El mapa admite `GET /api/events/{eventId}/module-data/map/search?q=...` y `/map/my-location`. Los registros `MAP:ZONE` y `MAP:POINT` pueden definir `visible`, `tableLabel` y `sectorLabel` en su payload. Las respuestas `REV:SURVEY_RESPONSE` indican la encuesta en `parentRecordId`.

El comprobante `GET /api/reservations/{id}/payments/{paymentId}/receipt` incluye referencia, monto, fecha y el aviso explícito de que el pago es una simulación académica.

Las pruebas usan H2 en modo PostgreSQL para cargar el contexto y validar el mapeo ORM sin depender de una base externa. En despliegue, Flyway ejecuta las migraciones PostgreSQL de `src/main/resources/db/migration`.
