# CAL / MAP: agenda y mapa

`GET /api/events/{eventId}/agenda` lista actividades. `GET .../agenda/now-next` entrega Ahora/Siguiente y `GET .../agenda/mine` reúne favoritos. El organizador crea con `POST .../agenda` y `{ "title": "Taller", "startsAt": "...", "endsAt": "...", "description": "...", "zone": "Salón A", "capacity": 40 }`; usa `PUT .../agenda/{id}` y `DELETE` para editar o cancelar. El invitado guarda/quita un favorito con `PUT .../agenda/{id}/favorite?enabled=true|false`. Si `NOT` está habilitado, se genera un aviso próximo a la actividad favorita.

`MAP:ZONE` y `MAP:POINT` se administran con las rutas del [motor modular](module-data.md). El payload puede incluir `visible`, `tableLabel`, `sectorLabel` y `description`. `GET /api/events/{eventId}/module-data/map/search?q=texto` busca entre puntos visibles (2 a 100 caracteres), y `/map/my-location` muestra puntos vinculados a mesa o sector de la invitación. Un invitado no ve puntos con `visible=false`.
