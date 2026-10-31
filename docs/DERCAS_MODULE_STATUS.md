# Estado de módulos DERCAS

Este inventario preliminar se basó en los controladores y guías `docs/frontend/*.md`. La comparación posterior con el DERCAS v2.0 completo (116 requisitos funcionales) está en [DERCAS_GAP_REVIEW.md](DERCAS_GAP_REVIEW.md). “Completo” aquí significa que existe el flujo REST documentado con persistencia y autorización del backend; no implica integración comprobada con proveedores externos o con los clientes web/móvil.

| Módulo | Estado | Cobertura comprobable | Limitación conocida |
|---|---|---|---|
| Plantillas de eventos | Completo (nuevo) | CRUD propio, catálogos integrados, aplicación y dependencias de módulos | Sin editor visual frontend en este repositorio |
| Privacidad | Completo (nuevo) | Preferencias de perfil/contacto aplicadas en networking | Preferencias generales de privacidad adicionales no definidas |
| Mensajería | Completo (lecturas) | Conversaciones, autorización por participante, marcas y estado de lectura | Sin recibos de entrega/lectura en tiempo real HTTP |
| Mapas, ubicaciones y zonas | Parcial | Registros de zonas y contenido MAP por evento | No hay proveedor de mapas/geocodificación ni GPS en vivo |
| Pedidos e inventario | Completo | Menú, disponibilidad, pedidos y estados/cancelación según guía ORD | Pagos de pedidos no reales |
| Filas virtuales | Completo | Ingreso, turno/posición, atención y notificación según guía QUE | Sin integración con pantallas físicas |
| Encuestas y votaciones | Completo | Encuestas, participación y votaciones con restricciones por usuario | Reglas avanzadas no especificadas fuera de contratos actuales |
| Juegos y trivia | Parcial | Progreso/puntuación de gamificación | No se documenta clasificación global dedicada |
| Galería y moderación | Completo | Archivos, galería y moderación con permisos | Almacenamiento externo depende de configuración del servidor |
| Networking y contactos | Completo (nuevo) | Perfiles, consentimiento, visibilidad/contacto, QR y reuniones | No incluye mensajería externa entre redes |
| Expositores y sesiones | Completo | Registros de expositores y sesiones ligados a módulos/eventos | No hay agenda de ponentes externa |
| Transporte, rutas y reservas | Parcial | Departures/reservas y avisos asociados | Sin seguimiento GPS ni operador de transporte integrado |
| Deportes | Parcial | Partidos y resultados | No se define administración completa de torneo/cuadros |
| Recursos | Completo | Recursos y certificados con validación del evento | Emisión depende de datos cargados por organizadores |
| Objetos perdidos | Completo | Publicación, consulta y gestión de objetos | Entrega física queda fuera de la API |
| Aforo | Completo | Conteo/estado de capacidad y accesos | Sin sensores físicos conectados |
| Administración y auditoría | Parcial | Usuarios, estados, roles solicitados, eventos, módulos, reportes y auditoría paginados | Sin consola REST genérica de permisos arbitrarios ni eliminación masiva |

Los módulos especializados usan la API de datos modulares del evento cuando así está definido (`docs/frontend/module-data.md`). No se añadieron rutas “de dominio” adicionales cuando las operaciones existentes ya cubren el contrato; las validaciones específicas existentes se mantienen en sus servicios. No se inventaron operaciones para las limitaciones indicadas.
