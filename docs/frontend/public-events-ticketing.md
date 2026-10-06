# Eventos públicos y entradas simuladas

Esta API implementa el flujo definido en [requisitos](../requirements/public-events-ticketing.md). No procesa dinero ni tarjetas. Todos los importes y comprobantes indican que se trata de una simulación.

## Catálogo público

Sin sesión:

- `GET /api/public/events?q=&type=&from=&page=0&size=20`: eventos `PUBLIC` publicados o en curso.
- `GET /api/public/events/{eventId}`: ficha y tipos habilitados, con `available` por tipo.

`from` es un instante ISO 8601. La ficha contiene información pública del evento; la lista de compradores y los QR no están disponibles aquí.

## Configuración del organizador

Con sesión del propietario o colaborador con `ALL`:

- `GET/PUT /api/events/{eventId}/ticket-settings` consulta o reemplaza la configuración. Para `PUT`, envía todos los campos:

```json
{
  "visibility": "PUBLIC",
  "allowSalesWhileRunning": false,
  "maxTicketsPerAccount": 4,
  "admissionCapacity": 200,
  "reservedInvitationCapacity": 20,
  "allowRevokeAfterCheckIn": false
}
```

`admissionCapacity: null` deja el aforo global sin límite duro; cada tipo mantiene su cupo. `estimatedCapacity` del evento sigue siendo informativo. Si se fija aforo global, las entradas y las plazas de invitación comparten el límite. No se pueden reducir límites por debajo de compromisos existentes.

- `POST /api/events/{eventId}/ticket-types` crea un tipo.
- `PUT /api/events/{eventId}/ticket-types/{typeId}` actualiza el tipo completo.
- `GET /api/events/{eventId}/ticket-types` lista tipos, incluidos los deshabilitados.

Ejemplo de tipo:

```json
{
  "name": "General",
  "description": "Acceso a la convención",
  "price": 50.00,
  "currency": "GTQ",
  "capacity": 100,
  "maxPerOrder": 4,
  "salesStart": "2026-10-01T00:00:00Z",
  "salesEnd": "2026-11-01T23:00:00Z",
  "enabled": true
}
```

Precio `0` crea entradas gratuitas. Usa fechas de venta coherentes con el evento.

## Compra y «Mis entradas»

Requiere cuenta autenticada. Envía una clave nueva y estable por intento en `Idempotency-Key` (8 a 120 caracteres); al reintentar por pérdida de red, reutiliza la misma clave y el mismo cuerpo.

```http
POST /api/events/{eventId}/ticket-orders
Idempotency-Key: order-12345678
Authorization: Bearer <token>
Content-Type: application/json

{"lines":[{"ticketTypeId":"<uuid>","quantity":2}]}
```

La respuesta devuelve un pedido `CONFIRMED`, `total`, `currency`, `reference`, `simulated: true` y un `tickets[]` por persona. Cada ticket incluye `qrPayload` con formato `eventflow:ticket:...`. Dibuja ese valor exacto como QR. La compra se confirma inmediatamente y no existe paso de pago externo.

- `GET /api/ticket-orders/mine`: pedidos del usuario.
- `GET /api/ticket-orders/{orderId}`: detalle de un pedido propio.
- `POST /api/ticket-orders/{orderId}/cancel`: cancela un pedido propio antes del inicio, siempre que ningún pase se haya utilizado.
- `POST /api/tickets/{ticketId}/regenerate`: reemplaza el QR de un pase propio activo; el QR anterior deja de funcionar.

El comprador conserva sus pedidos históricos aunque el evento termine o se cancele. Los QR deben mostrarse solo al dueño; no los uses como identificadores públicos ni los registres en analítica.

## Control de acceso y gestión

El lector actual acepta tanto `eventflow:invite:` como `eventflow:ticket:`:

```http
POST /api/events/{eventId}/check-in
Authorization: Bearer <token-del-personal>
Content-Type: application/json

{"qrPayload":"eventflow:ticket:<valor>"}
```

`POST /api/events/{eventId}/check-out` usa el mismo cuerpo. El personal necesita permiso `CHECK_IN`. Una entrada admite un ingreso simultáneo; la política `reentryAllowed` del evento controla ingresos posteriores a una salida. La respuesta de un QR de entrada contiene `ticket`, `action` y `checkedAt`.

- `GET /api/events/{eventId}/ticket-orders?page=0&size=20`: pedidos para propietario o colaborador con permiso `TICKETS`; los QR aparecen como `null`.
- `POST /api/events/{eventId}/ticket-orders/{orderId}/cancel` con `{"reason":"..."}`: cancela un pedido completo sin pases utilizados.
- `GET /api/events/{eventId}/ticket-sales`: cantidad de pedidos, entradas activas, personas dentro y total/reversiones simuladas.
- `POST /api/events/{eventId}/tickets/{ticketId}/revoke` con `{"reason":"..."}`: anula un pase no usado; uno usado se puede revocar solo si `allowRevokeAfterCheckIn=true` y no libera cupo de venta.

Los errores siguen el formato general de la API. Trata `409 CONFLICT` como agotamiento, límite por cuenta, QR repetido o cambio incompatible de configuración y muestra el mensaje al usuario.
