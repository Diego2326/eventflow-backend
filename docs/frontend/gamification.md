# GAM: hitos, misiones e insignias

El organizador crea `GAM:MILESTONE`, `GAM:MISSION` y `GAM:BADGE` desde el [motor modular](module-data.md). Una misión lleva condiciones:

```json
{ "title": "Explorador", "payload": { "requirements": [{ "recordId": "<milestone-id>", "action": "CHECK_IN" }] } }
```

Las acciones aceptadas como condición son `CHECK_IN`, `VOTE`, `ANSWER`, `SAVE`, `RESERVE` y `JOIN` sobre un registro del mismo evento. Una insignia usa `payload.missionId`. Las condiciones publicadas no se editan.

El personal con `CHECK_IN` registra un hito con `POST /api/events/{eventId}/gamification/milestones/{milestoneId}/guests/{invitationId}`. El invitado consulta `GET /api/events/{eventId}/gamification/progress`: cada misión contiene `completed`, `required` y `finished`, más `badgeIds` ganadas. `POST .../gamification/sync` asocia las insignias de misiones terminadas, de forma idempotente. Llama a `sync` después de una actividad que pueda completar una misión y refresca `progress`.
