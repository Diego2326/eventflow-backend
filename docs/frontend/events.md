# EVT / MOD / ADM: eventos y configuración

`POST /api/events` requiere rol `ORGANIZER` o `ADMIN`. Cuerpo mínimo: `{ "name": "Expo", "type": "CUSTOM", "startsAt": "2026-10-05T20:00:00Z" }`. Campos opcionales: `endsAt`, `timezone`, `location`, `estimatedCapacity`, `budget`, `description`, `reentryAllowed`. El evento inicia en `DRAFT`. `GET /api/events` devuelve los eventos accesibles y `GET /api/events/{id}` devuelve uno. `PATCH` modifica campos permitidos según su estado.

`POST /api/events/{id}/status/{status}` aplica transiciones a `PUBLISHED`, `RUNNING`, `FINISHED` o `CANCELLED`. Consulta `GET /api/events/{id}/dashboard` para conteos de invitados, ingresos, reservaciones y asistencia. Un evento finalizado rechaza operaciones exclusivas de ejecución.

## Módulos

`GET /api/modules/catalog` entrega el catálogo global. `GET /api/events/{id}/modules` y `/modules/navigation` entregan módulos habilitados y visibles. Configura uno con `PUT /api/events/{id}/modules/{code}`:

```json
{ "enabled": true, "order": 10, "featured": false, "configuration": { "guestVisible": true } }
```

El backend valida dependencias entre módulos. La configuración de `INT` admite `guestbookModeration` y `maxSongRequestsPerUser`. Los colaboradores se gestionan con `POST/GET /api/events/{id}/collaborators` y `DELETE /api/events/{id}/collaborators/{userId}`. El cuerpo de alta es `{ "userId": "<uuid>", "permissions": ["CHECK_IN"] }`; `ALL` concede todos los permisos del evento.

## Administración global

Un `ADMIN` dispone de `/api/admin/users`, `/api/admin/role-requests`, `/api/admin/reports` y `PATCH /api/admin/modules/{code}`. El usuario puede presentar un reporte con `POST /api/reports`. Al desactivar globalmente un módulo, no se puede agregar a configuraciones nuevas. Las respuestas de moderación y cambios de rol quedan auditados.
