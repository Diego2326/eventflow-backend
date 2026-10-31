# GAL: archivos y galería

`POST /api/events/{eventId}/files/upload-url` inicia una carga de foto para `GAL`. Envía `module`, `fileName`, `contentType`, `sizeBytes` y `official` como JSON. La API valida el evento y entrega un `fileId`, una URL firmada temporal y los encabezados requeridos. El cliente hace un `PUT` del archivo **directamente a Neon Object Storage** usando esa URL; los bytes de la foto no pasan por Cloud Run. Después llama `POST /api/events/{eventId}/files/{fileId}/complete-upload` para que la API verifique el tamaño y el tipo del objeto y active sus metadatos. Si el `PUT` falla, elimina el intento con `DELETE /api/events/{eventId}/files/{fileId}/upload`.

La URL firmada solo autoriza una transferencia durante un periodo corto: no es la dirección permanente del archivo. PostgreSQL conserva la ruta estable del objeto; cada respuesta de lectura crea una URL firmada nueva para el bucket privado. Las fotos de galería no se aceptan por la ruta multipart para impedir que el cliente las envíe por la API.

Para archivos que no son fotos de galería, `POST /api/events/{eventId}/files` conserva `multipart/form-data` con `module=RSC`/`SES`/`EXH`, parte `file`, `recipientUserId` opcional para archivos privados y `official=true` opcional. Se aceptan JPEG, PNG, WebP, PDF, texto y PPTX hasta 10 MB. Una foto de invitado puede quedar `PENDING` hasta moderación; el organizador decide con `PATCH /api/events/{eventId}/files/{fileId}/moderation` y `{ "status": "ACTIVE" }` o `REJECTED`.

Para permitir aportes de asistentes, habilita el módulo `GAL` con `configuration.allowGuestUploads=true`. Se recomienda mantener `moderationRequired=true` para que las fotos de invitados esperen revisión; los usuarios con permiso `FILES` pueden aprobarlas o rechazarlas desde la app.

`GET /api/events/{eventId}/files?module=GAL` lista archivos visibles, con `moderationStatus`, `official` y `downloadUrl`. La API autoriza la lectura y redirige al bucket; los bytes se descargan desde Neon. `DELETE /api/events/{eventId}/files/{fileId}` retira un archivo. Para galería oficial, el organizador sube con `official=true` después de finalizar el evento; el backend rechaza esa marca antes de `FINISHED` o fuera de `GAL`. El frontend puede separar las fotos oficiales filtrando `official`.

No uses directamente la ruta del objeto en el proveedor de almacenamiento; usa siempre `downloadUrl`, que comprueba invitación, módulo, moderación y destinatario. La respuesta contiene un enlace firmado de corta duración generado desde la ruta persistida.

El backend admite Supabase Storage y Neon Object Storage S3. Para Neon, configura `APP_STORAGE_PROVIDER=neon`, `APP_STORAGE_BUCKET=uploads`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_ENDPOINT_URL_S3` y `AWS_REGION`; conserva el bucket privado. La API valida y autoriza; las transferencias van directas al bucket con URL firmada.
