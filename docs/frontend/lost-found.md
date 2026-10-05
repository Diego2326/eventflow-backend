# LNF: objetos perdidos y encontrados

El invitado crea `LNF:LOST_ITEM` mediante `POST /api/events/{eventId}/module-data/LNF/LOST_ITEM` con `title` descriptivo y `payload.category` opcional. El reporte inicia `OPEN` y solo lo ven su autor y el organizador. El personal publica `LNF:FOUND_ITEM` con descripción; inicia `ACTIVE`, visible al público autorizado. El backend rechaza campos de datos personales del propietario en un objeto encontrado.

El personal resuelve un reporte perdido con `PATCH .../records/{id}` y `{ "status": "RESOLVED" }`; para un objeto encontrado entregado usa `DELIVERED`. Las listas omiten elementos retirados o entregados a los invitados, pero el organizador conserva su consulta. El invitado no puede cambiar el estado de resolución.
