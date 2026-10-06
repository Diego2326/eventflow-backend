# ORD: menú y pedidos

El organizador crea `ORD:MENU_CATEGORY` con `title` y luego `ORD:MENU_ITEM` con `title` y `parentRecordId` de esa categoría activa mediante el [motor modular](module-data.md). Un artículo requiere `payload.price >= 0`, puede usar `payload.available` y `capacity` como existencias. La categoría no puede retirarse mientras tenga artículos publicados. El invitado consulta las listas `GET .../ORD/MENU_CATEGORY` y `GET .../ORD/MENU_ITEM`.

Para confirmar, crea `ORD:ORDER` con:

```json
{ "payload": { "items": [{ "itemId": "<menu-item-id>", "quantity": 2 }], "location": "Mesa 8" } }
```

La respuesta queda `PENDING`, contiene `payload.total` calculado por backend y descuenta existencias de modo atómico. No calcules el importe final solo en cliente. El personal con permiso `ORDERS` cambia estado en `PATCH /api/events/{eventId}/module-data/records/{orderId}` usando `status`: `ACCEPTED`, `PREPARING`, `READY`, `DELIVERED` o `CANCELLED` según la transición. El invitado puede cancelar mientras esté `PENDING`; una cancelación devuelve existencias. `GET .../ORD/ORDER` muestra al invitado sus pedidos y al personal los operativos. El cambio de estado genera aviso si `NOT` está habilitado.
