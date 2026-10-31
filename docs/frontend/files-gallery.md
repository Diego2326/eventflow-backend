# GAL: archivos y galería

`POST /api/events/{eventId}/files` usa `multipart/form-data` con `module=GAL` o `RSC`/`SES`/`EXH`, parte `file`, `recipientUserId` opcional para archivos privados y `official=true` opcional para galería oficial. Los archivos admitidos incluyen JPEG, PNG, WebP, PDF, texto y PPTX, hasta 10 MB. `GAL` solo acepta imágenes. Una foto de invitado puede quedar `PENDING` hasta moderación; el organizador decide con `PATCH /api/events/{eventId}/files/{fileId}/moderation` y `{ "status": "ACTIVE" }` o `REJECTED`.

Para permitir aportes de asistentes, habilita el módulo `GAL` con `configuration.allowGuestUploads=true`. Se recomienda mantener `moderationRequired=true` para que las fotos de invitados esperen revisión; los usuarios con permiso `FILES` pueden aprobarlas o rechazarlas desde la app.

`GET /api/events/{eventId}/files?module=GAL` lista archivos visibles, con `moderationStatus`, `official` y `downloadUrl`. `GET /files/{fileId}` descarga el contenido con autenticación y permisos del evento. `DELETE /files/{fileId}` retira un archivo. Para galería oficial, el organizador sube con `official=true` después de finalizar el evento; el backend rechaza esa marca antes de `FINISHED` o fuera de `GAL`. El frontend puede separar las fotos oficiales filtrando `official`.

No uses directamente la ruta del objeto en el proveedor de almacenamiento; usa siempre `downloadUrl`, que comprueba invitación, módulo, moderación y destinatario.
