# SES: ponentes, sesiones y asistencia

El organizador crea `SES:SPEAKER` y `SES:SESSION` desde el [motor modular](module-data.md). Usa el payload de la sesión para datos y referencias de ponentes/materiales publicados, y el módulo `RSC` para archivos. El invitado consulta `GET .../SES/SESSION` y puede guardar una sesión con acción `SAVE` (deshacer con `REMOVE`).

La asistencia **no** se marca con la acción genérica: el personal con permiso `CHECK_IN` envía `POST /api/events/{eventId}/sessions/{sessionId}/attendance/{invitationId}`. La invitación debe estar aceptada, vigente y vinculada a una cuenta; una asistencia repetida o cupo agotado devuelve 409. `GET .../records/{sessionId}/actions` permite al organizador ver registros y al invitado sus acciones. Los avisos `SESSION` se entregan a usuarios con asistencia a esa sesión.
