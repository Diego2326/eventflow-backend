# ESP / SRV / RES / PAY / REV: marketplace

Un propietario/proveedor aprobado crea publicaciones con `POST /api/offerings`. El cuerpo lleva `type` (`SPACE` o `SERVICE`), `name`, `category`, `description`, `location`, `capacity`, `price`, `attributes` e `imageUrls`. `attributes` es una **cadena JSON**, por ejemplo `"{\"features\":[\"wifi\",\"parking\"]}"`; `imageUrls` también es una cadena JSON. Actualiza con `PUT /api/offerings/{id}` y cambia estado mediante `POST /api/offerings/{id}/status/{status}`.

`GET /api/offerings?type=SPACE` acepta `category`, `location`, `minCapacity`, `maxPrice`, `minRating`, `feature`, `startsAt`, `endsAt` y `limit` (1 a 100). Envía siempre ambos extremos del intervalo de disponibilidad. La respuesta es una lista de publicaciones activas; una lista vacía indica que no hubo coincidencias. `GET/POST /api/offerings/{id}/availability` consulta o publica intervalos `{ "startsAt": "...", "endsAt": "...", "available": false }`.

## Contratación

1. El organizador crea una solicitud en `POST /api/reservations` con `eventId`, `offeringId`, `startsAt`, `endsAt` y `note` opcional; queda `PENDING`.
2. El proveedor decide con `POST /api/reservations/{id}/decision` y `{ "accepted": true }`; una aceptación conflictiva devuelve 409.
3. El organizador puede registrar **pago simulado** mediante `POST /api/reservations/{id}/payments` con `{ "amount": 100, "status": "PAID" }`. No representa cobro real. Consulta `GET /api/reservations/{id}/payments/{paymentId}/receipt` para el comprobante.
4. El proveedor concluye en `POST /api/reservations/{id}/complete`. Entonces el cliente puede publicar una sola reseña con `POST /api/reservations/{id}/reviews` y `{ "rating": 5, "comment": "..." }`. La calificación de la publicación se recalcula.

`POST /api/reservations/{id}/cancel` cancela si el estado lo permite. `GET /api/events/{eventId}/reservations` muestra las contrataciones del evento al organizador. Trata 403 al reseñar o cancelar sin permiso y 409 cuando ya existe una reseña, pago o reserva incompatible.
