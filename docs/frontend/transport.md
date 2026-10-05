# TRN: rutas y salidas

El organizador registra `TRN:ROUTE` y `TRN:DEPARTURE` con el [motor modular](module-data.md). Una salida requiere `title`, `capacity >= 1`, `startsAt` y `endsAt`; el payload puede incluir origen, destino, punto de encuentro y `requiresReservation`. Lista salidas mediante `GET /api/events/{eventId}/module-data/TRN/DEPARTURE`.

El invitado reserva con `POST .../records/{departureId}/actions` y `{ "action": "RESERVE" }`; libera con `CANCEL`. Una salida que declare `requiresReservation=false` no acepta la acción. La reserva cierra al empezar la salida y respeta cupo. Se envía un aviso `IN_APP` a reservas activas cuando faltan 30 minutos o menos, si `NOT` está habilitado y la preferencia del usuario lo permite. Para avisos manuales a pasajeros, usa audiencia `TRANSPORT` con el ID de la salida.
