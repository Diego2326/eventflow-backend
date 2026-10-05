# Integración frontend de EventFlow

La base es `/api`. Salvo los flujos públicos de autenticación, catálogo y acceso mediante token de invitación, envía `Authorization: Bearer <accessToken>`. Fechas y horas usan ISO 8601 con zona (`2026-10-05T20:00:00Z`); los IDs son UUID. El backend valida permisos y módulos habilitados por evento. Usa `GET /api/events/{eventId}/modules/navigation` para construir la navegación del invitado y no muestres módulos ausentes.

## Errores y sesiones

Los errores de aplicación usan `{ "error": { "code": "CONFLICT", "message": "...", "details": {}, "timestamp": "...", "path": "..." } }`. Los códigos principales son `BAD_REQUEST` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404) y `CONFLICT` (409). Trata `DATA_CONFLICT` (409) como una colisión de datos y `VALIDATION_ERROR` (400) como error por campos en `details`. Al recibir 401, renueva con `/api/auth/refresh` y reintenta una vez. Los token de acceso caducan; revocar o cambiar credenciales invalida sesiones según el flujo de autenticación.

El contrato exacto de todos los DTO está disponible en `/swagger-ui` y `/v3/api-docs`. Esta guía describe los recorridos y reglas que el frontend necesita para presentar cada módulo.

## Módulos

| Módulo | Guía |
|---|---|
| AUT / USR | [Autenticación y perfil](auth.md) |
| EVT / MOD / ADM | [Eventos, módulos y administración](events.md) |
| ESP / SRV / RES / PAY / REV | [Marketplace](marketplace.md) |
| INV / GST | [Invitaciones y acceso](invitations.md) |
| CAL / MAP | [Agenda y mapa](agenda-map.md) |
| AST / NOT / MSG | [Operación y comunicación](operations.md) |
| Motor de registros | [Datos modulares](module-data.md) |
| ORD | [Menú y pedidos](orders.md) |
| QUE / BKG / TRN | [Cola](queue.md), [reservas internas](booking.md), [transporte](transport.md) |
| INT / REV | [Interacción y encuestas](interaction.md) |
| NET | [Networking](networking.md) |
| GAM | [Gamificación](gamification.md) |
| GAL / RSC / EXH | [Archivos y galería](files-gallery.md), [recursos y certificados](resources.md) |
| SES / SPT | [Sesiones](sessions.md), [torneos](sports.md) |
| LNF / AFO | [Objetos perdidos](lost-found.md), [aforo](capacity.md) |

Las rutas de QR devuelven un valor para codificar. El frontend debe representarlo como imagen QR y, al leerlo, abrir la ruta indicada con una sesión autorizada. El backend no genera un PNG de QR.
