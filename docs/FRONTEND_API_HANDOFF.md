# EventFlow API handoff (Expo, Android, iOS, web)

## Base URL and authentication

- Local backend port: `http://localhost:5080` (from this PC).
- Android emulator: `http://10.0.2.2:5080`.
- iOS simulator: `http://localhost:5080`.
- Physical phones: use the computer's LAN address and allow port 5080 on the local network. For deployed clients use the HTTPS API origin.
- OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui/index.html`. The OpenAPI document is the complete route catalog, including feature modules.
- Send `Authorization: Bearer <accessToken>` on protected HTTP routes. JSON requests use `Content-Type: application/json`; timestamps are ISO-8601 UTC.
- Tokens are issued by login and Google login. Keep the access token in platform secure storage; rotate the refresh token by calling `/api/auth/refresh` and replace both returned tokens. Never store Google client secrets, SMTP credentials, Expo access tokens, database credentials or `JWT_SECRET` in a client.

Example common error:

```json
{"error":{"code":"FORBIDDEN","message":"No tienes permiso","details":{},"timestamp":"2026-10-30T20:00:00Z","path":"/api/events/..."}}
```

HTTP status mapping: `400` bad input/validation, `401` absent/invalid/revoked session, `403` authenticated but not authorized, `404` missing or deliberately hidden resource, `409` state/capacity/uniqueness conflict, `500` unexpected server failure. Validation errors use `error.code = VALIDATION_ERROR` and field names in `error.details`. `204` responses have no body.

## Registration, sessions and account email

`POST /api/auth/register` (201):

```json
{"name":"Ada","email":"ada@example.com","password":"Strong#Pass1","phonePrefixId":1,"phoneNumber":"55551234","birthDate":"1995-04-12","nationalityCountryCode":"GT"}
```

Response: `{"message":"Cuenta creada. Revisa el enlace de activación.","developmentToken":null}`. A production mail provider sends one-use email verification link. `POST /api/auth/verify-email` takes `{"token":"..."}`. Resend with `POST /api/auth/resend-verification` and `{"email":"..."}`. The response is intentionally generic for unknown addresses.

`POST /api/auth/login`: `{"identifier":"ada@example.com","password":"Strong#Pass1"}`. Google native sign-in sends the provider's **ID token** to `POST /api/auth/google`: `{"idToken":"<Google ID token>"}`. The backend checks Google's signed JWT against Google's rotating JWK keys, issuer, expiration, configured web/Android/iOS client audience and verified email. The client must send an ID token, not an authorization code or access token.

Both login routes return an `AuthResponse`:

```json
{"accessToken":"<JWT>","refreshToken":"<opaque token>","tokenType":"Bearer","expiresIn":3600,"userId":"<uuid>","name":"Ada","email":"ada@example.com","roles":["USER"],"profileComplete":true}
```

Google-created users get an active verified account and a JWT session; when no phone is known `profileComplete` is false. `GET /api/profile` returns the current profile. Complete it using `PATCH /api/profile`, e.g. `{"phonePrefixId":1,"phoneNumber":"55551234"}`. Existing verified-email accounts are linked by verified email; an account already linked to a different Google subject is rejected.

`POST /api/auth/refresh` accepts `{"refreshToken":"..."}` and returns the same `AuthResponse` shape with rotated tokens. Reuse of a rotated/revoked token returns 401. `POST /api/auth/logout` and `/api/auth/logout-all` require bearer auth and return 204.

Password recovery: `POST /api/auth/forgot-password` with `{"email":"..."}`, then the user follows the emailed link and the app submits `POST /api/auth/reset-password` with `{"token":"...","password":"NewStrong#Pass2"}`. Reset links are one-use and expire after 30 minutes; reset revokes sessions. Email verification lasts 24 hours. Email change: authenticated `POST /api/auth/request-email-change` with `{"email":"new@example.com"}`, then `POST /api/auth/confirm-email-change` with `{"token":"..."}`. Email-change links last 2 hours and confirmation revokes sessions.

Configure `FRONTEND_URL` and `AUTH_DEEP_LINK_BASE` on the server. The email contains web and app links, e.g. `https://app.example/verify-email?token=...` and `eventflow://auth/verify-email?token=...`. Register the `eventflow` scheme / Android App Links / iOS Universal Links in the clients and route `verify-email`, `reset-password`, and `confirm-email-change` to the corresponding API calls. Production must set `AUTH_MAIL_REQUIRED=true`, use a real SMTP/provider configuration, keep `AUTH_EXPOSE_TOKENS=false`, and must not return development tokens.

## Events, guest RSVP, schedule and capacity

- `GET /api/events` and `GET /api/events/{eventId}` list/read events visible to the caller.
- `POST /api/events` (organizer): `{"name":"Meetup","type":"CUSTOM","startsAt":"2026-11-20T18:00:00Z","endsAt":"2026-11-20T22:00:00Z","timezone":"America/Guatemala","location":"Guatemala City","estimatedCapacity":120,"description":"...","reentryAllowed":false}`. Returns the event object with `id`, status and supplied fields. Update with `PATCH /api/events/{eventId}`; publish/transition with `POST /api/events/{eventId}/status/{DRAFT|PUBLISHED|CANCELLED|COMPLETED}` subject to state rules.
- Event modules are configured with `PUT /api/events/{eventId}/modules/{code}` and `{"enabled":true,"order":0,"featured":false,"configuration":{"guestVisible":true}}`. Read module navigation at `GET /api/events/{eventId}/modules/navigation`.
- Organizer invitation creation: `POST /api/events/{eventId}/invitations` with `{"guestName":"Sam","guestEmail":"sam@example.com","allowedCapacity":2,"table":"A","seat":"12","sector":"North"}`. Response includes an opaque access `token` and QR payload `eventflow:invite:...`; do not put the token in analytics/logs.
- Public invite access: `GET /api/invitations/access/{token}`. RSVP: `POST /api/invitations/access/{token}/rsvp` with `{"accepted":true,"companions":["Alex"]}`. A signed-in invitee can link using `POST /api/invitations/access/{token}/link`; own invitations are at `GET /api/invitations/mine`. Linked RSVP is `POST /api/invitations/{invitationId}/rsvp` with the same body.
- Agenda: `GET /api/events/{eventId}/agenda`, `/agenda/now-next`, `/agenda/mine`; create with `POST /api/events/{eventId}/agenda` and `{"title":"Talk","description":"...","startsAt":"2026-11-20T19:00:00Z","endsAt":"2026-11-20T20:00:00Z","zone":"Hall A","responsible":"Host","capacity":40}`. Update via `PUT /agenda/{agendaId}`; favorite via `PUT /agenda/{agendaId}/favorite?enabled=true` (204); delete/cancel via `DELETE /agenda/{agendaId}` (204).
- Capacity reads: `GET /api/events/{eventId}/capacity/zones` and `/capacity/services`. Visibility and updates depend on enabled modules and event permissions.

## QR passes and check-in

Invite and ticket QR values are opaque bearer-like credentials; render them as QR locally and do not expose them in logs or URLs beyond the existing invite access link. The backend returns `qrPayload` on invitation creation and on ticket purchase.

- Staff check-in: `POST /api/events/{eventId}/check-in` with `{"qrPayload":"eventflow:invite:...","quantity":1}`; for a group pass, optional `memberIndex` selects a member. Check-out uses `/check-out` with the same JSON. Ticket payloads use `eventflow:ticket:...` and the same staff endpoints.
- Staff must have the event's access permission. A guest/invitee cannot check themselves in or out; HTTP tests assert 403. Successful invite operations return an updated invitation with `checkedIn` and member state; ticket operations return `{ "ticket": { ... }, "action":"CHECK_IN|CHECK_OUT", "checkedAt":"..." }`.
- Ticket purchase: public catalog `GET /api/public/events`, event detail `/api/public/events/{eventId}`. Authenticated purchase `POST /api/events/{eventId}/ticket-orders` requires unique `Idempotency-Key` header and body `{"lines":[{"ticketTypeId":"<uuid>","quantity":1}]}`. It currently returns a **simulated** order (no payment is captured) and private ticket QR payloads. Buyer orders: `GET /api/ticket-orders/mine`.

## Notifications and Expo push

- Create event notification (staff): `POST /api/events/{eventId}/notifications` with `{"title":"Update","body":"Doors open at 18:00","audienceType":"ALL","channel":"IN_APP"}`. Supported audience values include `ALL`, `TABLE`, `SECTOR`, `USER`, `SESSION`, and `TRANSPORT`; scoped values use `audienceValue`, and `USER` also uses `recipientUserId`. In-app read: `GET /api/events/{eventId}/notifications`.
- Register a device after login: `POST /api/profile/push-devices` with `{"expoPushToken":"ExponentPushToken[...]","platform":"ANDROID"}`; platform is `ANDROID`, `IOS`, or `WEB`. Response returns an ID/platform/timestamps but not the push token. List with `GET /api/profile/push-devices`; revoke with `DELETE /api/profile/push-devices/{deviceId}` (204). Each device belongs to exactly one user; revocation is owner-only.
- Delivery is triggered after the notification transaction commits and sends Expo batches of up to 100 tokens. Configure `EXPO_PUSH_ACCESS_TOKEN` (when the Expo project requires access authentication) only on the backend. When absent, in-app notifications and device registration still work, but outbound delivery is disabled. Provider delivery has been exercised against a local HTTP stub; this does not verify a real Expo project/token.

## Authenticated event chat (WebSocket/STOMP)

Connect to `ws://<host>:5080/ws` locally, or `wss://<api-host>/ws` in production. On STOMP `CONNECT`, include native header `Authorization: Bearer <accessToken>`. The server validates JWT **and active persisted session**. Use:

- `SUBSCRIBE /user/queue/events/{eventId}/messages` only after receiving `CONNECTED`.
- `SEND /app/events/{eventId}/messages` with JSON body `{"channel":"STAFF","body":"Hello","recipientUserId":null,"reservationId":null}`.

The message is persisted, and only the sender and explicitly addressed participant receive it through their own user destination. Event/module membership is checked at subscribe and each send; the persisted session is rechecked for each subscribe/send so logout or token revocation invalidates an open socket. Other STOMP destinations and commands are rejected. Existing HTTP chat remains: `POST /api/events/{eventId}/messages` with the same JSON and `GET /api/events/{eventId}/messages?channel=STAFF`. Set `WEBSOCKET_ALLOWED_ORIGINS` to explicit web origins for deployment; the default `*` is intended for local use and should not be kept for a browser-facing production deployment.

## Verification boundary

HTTP tests exercise registration/login/refresh/profile, Google account linking (with a test verifier), reset/email change, event creation, RSVP, agenda, capacity, QR authorization, tickets, push registration/revocation, a local Expo HTTP stub, WebSocket/STOMP and Swagger/OpenAPI. The Google verifier has a separate JWK signature/issuer/audience/expiry test. These prove backend behavior only. No React Native/Expo or web client source was present in this workspace, so client integration itself has not been claimed or tested. Full live provider delivery requires the operator's Google OAuth client IDs, mail provider and Expo project configuration in the server environment; no such secrets belong in either client.

## Added REST endpoints (local implementation)

- GET /api/profile/sessions and DELETE /api/profile/sessions/{id} expose/revoke only the caller's sessions; refresh hashes and IP addresses are never returned.
- GET /api/events/{eventId}/permissions returns the current user's effective event permissions. Owners can update collaborator permissions with PATCH /api/events/{eventId}/collaborators/{userId}.
- GET /api/reservations/mine and GET /api/reservations/{id} provide the caller's marketplace reservations.
- GET /api/events/{eventId}/notifications/{id}, POST .../{id}/read and POST .../read-all read and mark the caller-visible notification inbox. V23 persists read receipts per user; new notifications remain unread until marked.

These routes were added/verified locally. No remote database was changed.
- Messaging HTTP: `GET /api/events/{eventId}/messages/conversations?channel=STAFF` lists conversation summaries visible to the caller; `GET /api/events/{eventId}/messages/conversations/{conversationId}?channel=STAFF` reads one authorized thread. The existing `POST .../messages` and `GET .../messages?channel=...` remain available.
- Admin event/audit lists: `GET /api/admin/events` and `GET /api/admin/audit` require ADMIN. They return event records and audit records respectively.

Complete REST route list (177 mappings) is in [REST_ENDPOINT_CATALOG.md](REST_ENDPOINT_CATALOG.md); OpenAPI remains the runtime contract.

GET /api/profile/export returns the caller's profile, notification preferences, and sanitized session history for data portability. Refresh hashes and IP addresses are omitted.

## Plantillas de eventos

Todas las rutas requieren JWT. Crear, actualizar, eliminar y aplicar requieren rol `ORGANIZER` o `ADMIN`; las plantillas integradas se pueden consultar por cualquier usuario autenticado. Las plantillas personalizadas son privadas del propietario; otro usuario recibe 404.

- `GET /api/event-templates` → `[{ "id":"builtin-CONFERENCE", "name":"Conference", "eventType":"CONFERENCE", "moduleCodes":["CAL","SES"], "description":"...", "builtIn":true }]`. Incluye las plantillas guardadas por el usuario.
- `GET /api/event-templates/{templateId}` → un `EventTemplateResponse`.
- `POST /api/event-templates` → 201. Body: `{ "name":"Conference", "eventType":"CONFERENCE", "moduleCodes":["CAL","SES"], "description":"Agenda y sesiones" }`. Respuesta `EventTemplateResponse` con `builtIn:false`.
- `PUT /api/event-templates/{templateId}` → reemplaza los campos de la plantilla propia.
- `DELETE /api/event-templates/{templateId}` → 204.
- `POST /api/event-templates/apply/{templateId}` → 201. Body: `{ "name":"Mi conferencia", "startsAt":"2026-11-20T15:00:00Z", "endsAt":"2026-11-20T23:00:00Z", "timezone":"America/Guatemala", "location":"Guatemala", "estimatedCapacity":300, "budget":0, "description":"...", "reentryAllowed":false }`. Respuesta: `{ "templateId":"...", "event":{...}, "enabledModules":["CAL","SES"] }`. El backend valida disponibilidad y dependencias, y habilita dependencias antes que los módulos solicitados.

Errores: 401 sin sesión, 403 sin rol de organización, 404 para plantilla privada inexistente/no propia, 400 por campos inválidos y 409 si un módulo está deshabilitado globalmente o si hay dependencia cíclica.

## Privacidad del perfil

- `GET /api/profile/privacy` → `{ "networkingProfileVisible":true, "networkingContactVisible":true, "updatedAt":null }` si aún no existe preferencia guardada.
- `PUT /api/profile/privacy` body `{ "networkingProfileVisible":false, "networkingContactVisible":false }` → devuelve las preferencias guardadas con `updatedAt`.

Ambos endpoints usan la identidad del JWT, sin aceptar un userId del cliente. La preferencia de perfil controla la visibilidad en búsqueda/QR/reuniones de networking; la preferencia de contacto omite el campo `contact` cuando otro usuario consulta el perfil. 401 sin sesión; 400 para JSON/campos inválidos.

## Lecturas de mensajes

Las rutas requieren participación/autorización en la conversación y `channel` como query parameter (por ejemplo `STAFF`).

- `POST /api/events/{eventId}/messages/conversations/{conversationId}/read?channel=STAFF` marca como leídos los mensajes recibidos por el usuario actual. Respuesta `{ "conversationId":"...", "markedCount":1 }`; repetición es idempotente y puede devolver `markedCount:0`.
- `GET /api/events/{eventId}/messages/conversations/{conversationId}/read-state?channel=STAFF` → lista de mensajes con `{ "messageId":"...", "senderUserId":"...", "recipientUserId":"...", "readBy":[{ "id":"...", "messageId":"...", "userId":"...", "readAt":"2026-10-30T12:00:00Z" }] }`.

Las conversaciones ajenas o inexistentes responden 404 para no revelar existencia; sin JWT, 401. Las rutas de envío/listado existentes permanecen sin cambios de contrato.
## Paginación administrativa

Las listas `GET /api/admin/users`, `/events`, `/audit`, `/role-requests` y `/reports` requieren ADMIN. Opcionalmente acepta `?page=0&size=20` (página base cero, tamaño máximo 100). El JSON continúa siendo el arreglo existente; se devuelven los encabezados `X-Page`, `X-Page-Size`, `X-Total-Count`, `X-Total-Pages` y `X-Has-Next`. Sin query conserva el arreglo completo para compatibilidad. Un `page` negativo, `size` fuera de 1–100 o un offset que excede el entero soportado responde 400.