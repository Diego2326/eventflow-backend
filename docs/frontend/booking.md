# BKG: reservas de actividades

El organizador crea `BKG:ACTIVITY` con `title`, `capacity >= 1`, `startsAt`, `endsAt` y `payload.cancelBeforeMinutes` opcional. `currentCount` indica reservas activas. El invitado lista `GET /api/events/{eventId}/module-data/BKG/ACTIVITY` y reserva mediante `POST .../records/{activityId}/actions` con `{ "action": "RESERVE" }`.

La reserva cierra al comenzar la actividad; cupo agotado o reserva activa repetida devuelven 409. Cancela con acción `CANCEL`; el cupo se libera si sigue dentro del plazo configurado. La acción admite una persona por cuenta y se puede volver a reservar tras una cancelación válida.
