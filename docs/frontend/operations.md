# AST / NOT / MSG: asistencia y comunicación

## Asistencia

`POST /api/events/{eventId}/assistance` envía `{ "category": "FIRST_AID", "details": "...", "location": "...", "invitationId": "<uuid>" }`. Las categorías deben estar habilitadas en el evento; primeros auxilios/emergencia tienen prioridad superior. `GET` en la misma ruta lista solicitudes según permisos y `PATCH .../assistance/{id}` permite al personal actualizar el estado. Invitados con token pueden usar `/api/invitations/access/{token}/assistance` y consultar el historial en la misma ruta con `GET`.

## Notificaciones

`POST /api/events/{eventId}/notifications` requiere permiso `NOTIFICATIONS`:

```json
{ "title": "Cambio de sala", "body": "Revisa la agenda", "audienceType": "SESSION", "audienceValue": "<session-id>", "channel": "IN_APP" }
```

Audiencias: `ALL`, `TABLE`, `SECTOR`, `USER` (con `recipientUserId`), `SESSION` (ID de `SES:SESSION`) y `TRANSPORT` (ID de `TRN:DEPARTURE`). `GET` devuelve solo avisos del usuario o audiencia compatible. Los avisos automáticos de agenda, pedidos, cola y transporte aparecen aquí cuando `NOT` está habilitado. Por ahora el canal admitido es `IN_APP`.

`GET /api/profile/notifications` devuelve avisos personales dirigidos a la cuenta, incluidos cambios de reservaciones, sin depender del módulo `NOT`. `POST /api/profile/notifications/{id}/read` marca como leído un aviso propio; otro usuario recibe 404.

## Mensajes

`POST /api/events/{eventId}/messages` usa `{ "channel": "...", "body": "...", "recipientUserId": "<uuid>" }` o `reservationId` cuando corresponda. `GET /api/events/{eventId}/messages?channel=...` consulta la conversación autorizada. Lee la lista y permisos antes de mostrar el canal; el backend valida interlocutores y evento.

El propietario configura los canales de atención con `PUT /api/events/{eventId}/modules/MSG` y `configuration.guestChannels`, por ejemplo `["STAFF", "INFO"]`. El valor predeterminado es `["STAFF"]`; `[]` cierra todos los canales de atención y bloquea nuevos mensajes, sin borrar el historial. Los nombres deben estar en mayúsculas, ser únicos y tener como máximo 30 caracteres. Las conversaciones ligadas a reservaciones se rigen por sus participantes y no por esta lista.

## Lecturas de conversación

`POST /api/events/{eventId}/messages/conversations/{conversationId}/read?channel=STAFF` registra como leídos los mensajes entrantes del usuario autenticado y responde `{ "conversationId":"...", "markedCount":1 }`. `GET .../{conversationId}/read-state?channel=STAFF` devuelve cada mensaje de la conversación y `readBy` con los recibos `{id,messageId,userId,readAt}`. Solo participantes autorizados pueden consultar o marcar. La repetición de POST es segura e idempotente.
