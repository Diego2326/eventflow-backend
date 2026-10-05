# AST / NOT / MSG: asistencia y comunicación

## Asistencia

`POST /api/events/{eventId}/assistance` envía `{ "category": "FIRST_AID", "details": "...", "location": "...", "invitationId": "<uuid>" }`. Las categorías deben estar habilitadas en el evento; primeros auxilios/emergencia tienen prioridad superior. `GET` en la misma ruta lista solicitudes según permisos y `PATCH .../assistance/{id}` permite al personal actualizar el estado. Invitados con token pueden usar `/api/invitations/access/{token}/assistance` y consultar el historial en la misma ruta con `GET`.

## Notificaciones

`POST /api/events/{eventId}/notifications` requiere permiso `NOTIFICATIONS`:

```json
{ "title": "Cambio de sala", "body": "Revisa la agenda", "audienceType": "SESSION", "audienceValue": "<session-id>", "channel": "IN_APP" }
```

Audiencias: `ALL`, `TABLE`, `SECTOR`, `USER` (con `recipientUserId`), `SESSION` (ID de `SES:SESSION`) y `TRANSPORT` (ID de `TRN:DEPARTURE`). `GET` devuelve solo avisos del usuario o audiencia compatible. Los avisos automáticos de agenda, pedidos, cola y transporte aparecen aquí cuando `NOT` está habilitado. Por ahora el canal admitido es `IN_APP`.

## Mensajes

`POST /api/events/{eventId}/messages` usa `{ "channel": "...", "body": "...", "recipientUserId": "<uuid>" }` o `reservationId` cuando corresponda. `GET /api/events/{eventId}/messages?channel=...` consulta la conversación autorizada. Lee la lista y permisos antes de mostrar el canal; el backend valida interlocutores y evento.
