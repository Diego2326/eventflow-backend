# SPT: equipos y bracket

El organizador crea `SPT:PARTICIPANT` o `SPT:TEAM`, y luego `SPT:MATCH` con `payload.round`, `participantAId` y `participantBId`. En ronda inicial se necesitan dos IDs distintos del mismo evento. Una ronda posterior puede iniciar con espacios vacíos. Para avanzar al ganador, el partido previo lleva `payload.nextMatchId` y `payload.nextSlot` (`A` o `B`). La lista `GET .../SPT/MATCH` sirve para el bracket público del evento.

El organizador o colaborador con permiso `SPORTS` confirma el marcador con `POST /api/events/{eventId}/matches/{matchId}/result` y `{ "scoreA": 2, "scoreB": 1 }`. No se permiten empates en un partido que debe producir ganador. La respuesta incluye `winnerId` y `nextMatchId`; el partido pasa a `COMPLETED`, guarda `scoreA`, `scoreB`, `winnerId` en payload y coloca al ganador en la siguiente ronda. Una segunda confirmación devuelve 409. Recarga ambos partidos después de reportar.
