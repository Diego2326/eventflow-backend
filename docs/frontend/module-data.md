# Motor de datos modulares

Todos los registros viven bajo `/api/events/{eventId}/module-data`. Para crear: `POST /{module}/{type}` con:

```json
{ "title": "Nombre", "payload": {}, "status": "ACTIVE", "capacity": 30, "startsAt": "2026-10-05T20:00:00Z", "endsAt": "2026-10-05T21:00:00Z", "parentRecordId": null }
```

`GET /{module}/{type}` lista registros visibles para el usuario; `GET /records/{id}` devuelve uno. `PATCH /records/{id}` acepta campos opcionales de título, payload, estado, capacidad y horario según el módulo y el permiso. `DELETE /records/{id}` lo archiva. La respuesta de registro incluye `id`, `eventId`, `moduleCode`, `recordType`, `ownerUserId`, `status`, `payload`, `capacity`, `currentCount`, horarios y fecha de creación. Para cupos disponibles calcula `capacity - currentCount` si hay capacidad.

`POST /records/{id}/actions` usa `{ "action": "SAVE", "payload": {}, "quantity": 1 }`; `GET /records/{id}/actions` muestra las acciones propias del invitado o todas al organizador. Las acciones se limitan por **tipo de registro**: no envíes acciones genéricas a registros no compatibles. `GET /my-resources` devuelve recursos, sesiones y expositores guardados. Las reglas particulares y rutas especializadas están en cada archivo de módulo.

Los invitados solo reciben registros publicados y visibles; los registros privados, pendientes y de otros usuarios devuelven 404 o se omiten de listas. Usa `moduleCode` y `recordType` de la respuesta para decidir el componente de UI; no supongas que un `payload` arbitrario implica una operación permitida.

El título admite hasta 200 caracteres. El JSON de `payload` admite hasta 16 KiB por registro y 4 KiB por acción. Las operaciones que crean o actualizan registros validan los campos específicos de cada tipo antes de publicarlos.
