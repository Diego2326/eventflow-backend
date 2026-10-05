# RSC / EXH: recursos, stands y certificados

`RSC:RESOURCE`, `EXH:EXHIBITOR` y `EXH:STAND` se publican en el [motor modular](module-data.md). Un invitado guarda un recurso con acción `SAVE` sobre el registro o `POST /api/events/{eventId}/resources/{resourceId}/save`; quita el guardado con acción `REMOVE`. `GET /api/events/{eventId}/module-data/my-resources` muestra su lista personal. Los archivos asociados se gestionan con las rutas de [archivos](files-gallery.md).

`GET /api/events/{eventId}/resources/{resourceId}/qr` devuelve una ruta codificable como QR. El escáner abre `GET /api/events/{eventId}/resources/{resourceId}` y luego ofrece el botón de guardado. La consulta y el guardado requieren acceso al evento y recurso activo.

Para publicar un certificado, el organizador primero sube un PDF bajo `module=RSC` con `recipientUserId` del invitado. Después de terminar el evento usa `POST /api/events/{eventId}/certificates`:

```json
{ "recipientUserId": "<uuid>", "fileId": "<uuid-del-pdf>", "title": "Constancia de asistencia" }
```

El destinatario necesita check-in vigente; el PDF debe ser privado para él y un certificado duplicado devuelve 409. `GET /api/events/{eventId}/certificates/mine` devuelve solo los del usuario y su `downloadUrl`. No construyas URLs directas al almacenamiento.
