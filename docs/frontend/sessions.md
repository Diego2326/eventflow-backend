# SES: ponentes, sesiones y asistencia

El organizador crea primero `SES:SPEAKER` y después `SES:SESSION` con `parentRecordId` del ponente mediante el [motor modular](module-data.md). Un material `RSC:RESOURCE` se vincula con `payload.sourceRecordId` igual al ID de la sesión. El invitado consulta `GET /api/events/{eventId}/sessions/{sessionId}` para recibir sesión, ponente y recursos asociados; también puede guardar una sesión con acción `SAVE` (deshacer con `REMOVE`).

La asistencia **no** se marca con la acción genérica: el personal con permiso `CHECK_IN` envía `POST /api/events/{eventId}/sessions/{sessionId}/attendance/{invitationId}`. La invitación debe estar aceptada, vigente y vinculada a una cuenta; una asistencia repetida o cupo agotado devuelve 409. `GET .../records/{sessionId}/actions` permite al organizador ver registros y al invitado sus acciones. Los avisos `SESSION` se entregan a usuarios con asistencia a esa sesión.
