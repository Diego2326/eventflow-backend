# AFO: aforo y servicios

El organizador crea primero una zona `MAP:ZONE` o una mesa `GST:SEATING_AREA` activa. Después crea `AFO:ZONE_CAPACITY` con `title`, `capacity` y payload `{ "zoneId": "<id-zona-o-mesa>", "occupied": 0 }`. Una zona admite un aforo publicado y no puede retirarse mientras ese aforo siga activo. `GET /api/events/{eventId}/capacity/zones` devuelve `zoneId`, `capacity`, `occupied`, `available` y `state`: `AVAILABLE`, `NEAR_LIMIT` (80 % o más) o `FULL`. Actualiza el conteo en `PATCH .../module-data/records/{id}` con el payload completo.

Para un servicio crea `AFO:SERVICE_STATUS` con `payload.state` igual a `AVAILABLE`, `BUSY`, `PAUSED` o `CLOSED`. `GET /api/events/{eventId}/capacity/services` muestra el estado vigente de cada servicio activo. Los invitados solo ven el módulo cuando está habilitado y publicado para ellos.
