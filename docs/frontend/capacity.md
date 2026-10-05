# AFO: aforo y servicios

El organizador crea `AFO:ZONE_CAPACITY` con `title`, `capacity` y `payload.occupied` entero entre 0 y el límite. `GET /api/events/{eventId}/capacity/zones` devuelve `capacity`, `occupied`, `available` y `state`: `AVAILABLE`, `NEAR_LIMIT` (80 % o más) o `FULL`. Actualiza el conteo en `PATCH .../module-data/records/{id}` con el payload completo.

Para un servicio crea `AFO:SERVICE_STATUS` con `payload.state` igual a `AVAILABLE`, `BUSY`, `PAUSED` o `CLOSED`. `GET /api/events/{eventId}/capacity/services` muestra el estado vigente de cada servicio activo. Los invitados solo ven el módulo cuando está habilitado y publicado para ellos.
