# NET: perfiles y reuniones

El invitado participa creando `NET:PROFILE` con `payload.consent=true`, `visible=true`, `displayName`, `bio`, `organization`, `interests` (lista de hasta 20 cadenas) y `contact` opcional. Solo se permite un perfil activo por persona y evento. Usa `PATCH .../records/{profileId}` con `payload.visible=false` para retirarlo de búsqueda y sugerencias. `GET /api/events/{eventId}/module-data/networking/suggestions` devuelve hasta 20 perfiles visibles ordenados por intereses en común, únicamente si el solicitante también participa.

`GET /api/events/{eventId}/networking/qr` devuelve `profileId` y `value` para representar como QR. Al escanear, el usuario autenticado abre `GET /api/events/{eventId}/networking/profiles/{profileId}`; el backend comprueba acceso al evento y consentimiento y devuelve solo los campos compartidos. Un perfil oculto responde 404.

Para solicitar reunión usa `POST /api/events/{eventId}/networking/meetings`:

```json
{ "participantUserId": "<uuid>", "startsAt": "2026-10-05T20:00:00Z", "endsAt": "2026-10-05T20:15:00Z", "location": "Sala B" }
```

Ambos participantes necesitan perfil visible. `GET` en esa ruta lista solo reuniones propias. El invitado destinatario responde en `POST .../{id}/accept` o `/decline`; cualquiera de los dos cancela en `/cancel`. Aceptar bloquea choques con reuniones aceptadas de cualquiera de los participantes. La reunión empieza `PENDING` y puede pasar a `ACCEPTED`, `DECLINED` o `CANCELLED`.

## Privacidad de networking

Consulta `GET /api/profile/privacy` y cambia las preferencias propias mediante `PUT` con `{ "networkingProfileVisible":true, "networkingContactVisible":false }`. Las preferencias se aplican a resultados de networking, perfil por QR, solicitudes de reunión y campos compartidos. Si el perfil es privado, otros usuarios no lo pueden descubrir; el titular tampoco puede generar un QR público de perfil. Si solo el contacto es privado, el campo `contact` se omite en respuestas de perfil para terceros. El comportamiento inicial para usuarios sin preferencia guardada es visible/compartible para conservar el contrato anterior.