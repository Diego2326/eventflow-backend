# INT / REV: participación y encuestas

Todos los tipos se listan con `GET /api/events/{eventId}/module-data/INT/{type}` y se crean con `POST` en la misma ruta. Los registros visibles pueden tener `startsAt`/`endsAt`; las acciones fuera de la ventana o en estado distinto de `ACTIVE` devuelven 409.

## Encuestas y preguntas

`INT:POLL` recibe `payload.options` como `[{ "id": "a", "label": "A" }, ...]`, `allowMultiple` y `resultsPublished`. El invitado vota con `POST .../records/{pollId}/actions` y `{ "action": "VOTE", "payload": { "optionId": "a" } }`. Consulta `/records/{pollId}/poll-results`: el organizador ve resultados; el invitado solo después de `resultsPublished=true`. La encuesta puede pasar a `CLOSED`.

`INT:QUESTION_BOARD` configura `moderationRequired` y `allowVotes`. El invitado crea `INT:QUESTION` con `parentRecordId` del tablero y `title`; queda `PENDING` si hay moderación. El organizador la aprueba con `PATCH .../records/{id}` y `{ "status": "ACTIVE" }`. Los votos usan acción `VOTE` y no se repiten.

## Trivia, sorteos y mensajes

`INT:TRIVIA` requiere opciones como una encuesta, `correctOptionId` y `points` opcional en payload. La respuesta usa acción `ANSWER` con `payload.optionId`; el invitado no recibe la opción correcta ni su puntuación mientras la trivia está activa. El organizador la cierra con `PATCH .../records/{id}` (`status=CLOSED`, `payload.resultsPublished=true` junto con sus opciones) y publica resultados en `GET .../records/{id}/trivia-results`. El invitado ve únicamente su puntuación; el organizador puede ver todas. `INT:DRAW` lo configura el organizador; ejecuta `POST /api/events/{eventId}/draws/{drawId}/run`. La respuesta da `invitationId`, `userId` si está vinculado y `guestName`. Solo participan invitaciones con check-in vigente; el sorteo se ejecuta una vez y excluye ganadores previos.

El invitado publica `INT:GUEST_MESSAGE` con `title`; puede quedar `PENDING` hasta moderación según `guestbookModeration`. `INT:SONG` registra una propuesta por canción; el límite por usuario sale de `maxSongRequestsPerUser` (3 por defecto) en la configuración del módulo. Vota una canción activa con acción `VOTE`.

## Encuesta posterior REV

El organizador crea `REV:SURVEY` con `payload.anonymous=true|false`. Después de `FINISHED`, el invitado responde creando `REV:SURVEY_RESPONSE` con `parentRecordId` de la encuesta y las respuestas en `payload`. Solo admite una respuesta por cuenta y encuesta. En modo anónimo `ownerUserId` es nulo; el frontend no debe pedir identidad en el formulario. No se puede cambiar el modo después de la primera respuesta. El organizador consulta las respuestas con `GET .../REV/SURVEY_RESPONSE`.
