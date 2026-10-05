# QUE: cola virtual

El organizador crea `QUE:QUEUE` con `title`, `capacity` opcional y `payload.notificationThreshold` de 0 a 20. El estado `ACTIVE` acepta ingresos; `CLOSED` impide nuevos. Lista con `GET /api/events/{eventId}/module-data/QUE/QUEUE`.

El invitado usa `POST /api/events/{eventId}/module-data/records/{queueId}/actions` con `{ "action": "JOIN" }` o `{ "action": "LEAVE" }`. No se puede ingresar dos veces mientras el turno siga activo ni superar cupo. Después de salir se puede volver a ingresar al final. `GET /api/events/{eventId}/module-data/queues/{queueId}/position` responde `position` (nulo si no está en cola), `peopleAhead` y `waiting`. Si `NOT` está habilitado, el aviso de turno próximo se emite una vez por ingreso.
