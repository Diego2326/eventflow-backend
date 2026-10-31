# Revisión de cumplimiento del DERCAS v2.0

Fuente: `EventFlow_DERCAS_v2_0.pdf` proporcionado por el equipo (40 páginas, 116 requisitos funcionales). Esta revisión compara **el backend de este repositorio** con los criterios del documento. Una API existente no demuestra por sí sola que la app web o móvil esté terminada.

La brecha del cliente móvil está detallada en [DERCAS_MOBILE_GAP_REVIEW.md](DERCAS_MOBILE_GAP_REVIEW.md).

## Resultado por módulo

| Módulo | Requisitos | Backend disponible | Pendiente fuera del backend o de configuración |
|---|---|---|---|
| AUT | RF-AUT-001–014 | Registro, verificación, login, Google, sesiones, JWT, recuperación, desactivación, eliminación e invitaciones por token. RF-AUT-004 ahora exige teléfono y aceptación de términos antes de crear una cuenta nueva con Google. | Configurar SMTP para envío real de enlaces y `GOOGLE_CLIENT_ID` para login con Google en el despliegue. Pantallas de OAuth y consentimiento en el cliente. |
| USR | RF-USR-001–003 | Perfil, edición, cambio de correo verificado, roles y preferencias de notificación. | Conectar las pantallas de perfil y preferencias. |
| EVT | RF-EVT-001–004 | CRUD, estados, panel, permisos, colaboradores y métricas. | Panel visual en el cliente. |
| ESP | RF-ESP-001–003 | Publicación, filtros por características/capacidad/precio/fecha y disponibilidad con control de conflictos. | Presentación y comparación visual de resultados. |
| SRV | RF-SRV-001–003 | Publicación, búsqueda por categoría/ubicación/precio/calificación y disponibilidad. | Comparador visual en el cliente. |
| RES | RF-RES-001–003 | Solicitud, decisión y cancelación con control de estado y superposición. La decisión y cancelación emiten avisos personales a las partes. | Flujo de contratación en el cliente. |
| PAY | RF-PAY-001–002 | Pago y comprobante simulados, sin tarjetas. | Mostrar claramente la simulación en la interfaz. |
| MSG | RF-MSG-001–002 | Mensajes por evento o reservación, control de participantes, conversaciones, WebSocket y lecturas. RF-MSG-002 ahora permite abrir/cerrar canales de atención con `MSG.configuration.guestChannels`. | Cliente de chat y señalización de canales abiertos. |
| CAL | RF-CAL-001–004 | Agenda, ahora/siguiente, favoritos y avisos de actividades. | Vistas y sincronización del cliente. |
| MOD | RF-MOD-001–007 | Catálogo, plantillas sugeridas, agregar/quitar, parámetros, dependencias, orden, destacados y navegación autorizada. | Editor visual de módulos y pantalla de inicio dinámica. |
| INV | RF-INV-001–004 | Invitación, token público, RSVP, acompañantes, revocación y regeneración. | Compartir enlaces y pantallas RSVP. |
| GST | RF-GST-001–007 | Vinculación, Event Pass, QR, check-in/out, reingreso, asientos, miembros individuales y panel de conteos. | Renderizado/escaneo de QR y vista de grupo en el cliente. |
| MAP | RF-MAP-001–003 | Zonas/puntos, visibilidad, búsqueda y “mi ubicación” asociada. | Plano 2D interactivo y navegación visual en el cliente. |
| AST | RF-AST-001–004 | Categorías configurables, bandeja por permiso, transiciones y prioridad de primeros auxilios. | Bandeja y seguimiento visual. |
| ORD | RF-ORD-001–003 | Menú, artículo/categoría, inventario, cantidades, pedidos y estados. | Carrito y seguimiento visual. |
| QUE | RF-QUE-001–003 | Colas, turno/posición, cupo y avisos. | Vista de fila/turno. |
| BKG | RF-BKG-001–003 | Actividades reservables, cupos, bloqueo horario y cancelación. | Selector de horarios. |
| INT | RF-INT-001–006 | Encuestas, preguntas, trivia, sorteos, libro de mensajes y solicitudes/votos de canciones con límite por usuario. | Componentes de interacción y resultados en el cliente. |
| GAM | RF-GAM-001–003 | Pasaporte, hitos, misiones, progreso e insignias. | Presentación visual de progreso y recompensas. |
| GAL | RF-GAL-001–003 | Subida, moderación y galería oficial posterior. | Configurar Supabase Storage en el despliegue; pantallas de subida y galería. |
| NET | RF-NET-001–004 | Perfil voluntario, QR, sugerencias por intereses y reuniones con conflictos. | Escaneo QR, consentimiento y agenda de reuniones. |
| EXH | RF-EXH-001–003 | Directorio/detalle de expositores, favoritos y recursos guardables por QR. | Directorio, tarjetas y escaneo QR. |
| SES | RF-SES-001–003 | Ponentes, materiales vinculados y asistencia individual a sesiones. | Vista de sesión, ponente y materiales. |
| SPT | RF-SPT-001–003 | Participantes/equipos, programación/bracket y marcador con avance de ganador. | Vista de torneo y resultados. |
| TRN | RF-TRN-001–003 | Rutas/salidas, reserva de cupo y recordatorios. | Vistas de salidas y reservas. |
| LNF | RF-LNF-001–003 | Reportes perdidos/encontrados y resolución. | Formulario/listado visual. |
| AFO | RF-AFO-001–002 | Conteo de zonas y estados de servicio. | Indicadores visuales. |
| RSC | RF-RSC-001–003 | Recursos, certificados privados y “Mis recursos”. | Visor/descarga de recursos. |
| NOT | RF-NOT-001–003 | Avisos generales, segmentados y automáticos, preferencias y lecturas. | Configurar entrega push y pantallas de notificaciones. |
| REV | RF-REV-001–002 | Encuestas posteriores y reseñas de contrataciones concluidas. | Formularios de feedback y reseñas. |
| ADM | RF-ADM-001–003 | Usuarios/roles, reportes/auditoría y catálogo global de módulos. | Consola administrativa en el cliente. |

## Cambios cerrados en esta revisión

1. **RF-AUT-004 / CA-AUT-004-02:** una identidad Google nueva no crea cuenta hasta recibir teléfono y `acceptedTerms=true`; se registra `user_terms_accepted_at` (migración V27). El login de una cuenta ya existente conserva su contrato.
2. **RF-MSG-002 / CA-MSG-002-02:** `guestChannels` define los canales de atención abiertos. Un canal cerrado rechaza nuevos mensajes y conserva el historial.
3. **RF-MOD-005 / CA-MOD-005-02:** al intentar desactivar un módulo base, el error ahora enumera los módulos activos afectados.
4. **RF-RES-002 / CA-RES-002-02 y RF-RES-003 / CA-RES-003-02:** rechazo/aceptación y cancelación emiten avisos personales. El nuevo buzón de perfil permite consultarlos y marcarlos como leídos incluso si `NOT` no está habilitado en el evento.
5. **Registro con correo opcional:** cuando no hay host SMTP, el backend no intenta usar un remitente autoconfigurado sin servidor. El flujo de activación real sigue requiriendo SMTP.
6. **Acceso de documentación:** `/` redirige a Swagger UI con una ubicación relativa que conserva HTTPS. La guía `docs/API_ENDPOINTS.md` enumera las 179 operaciones HTTP, incluidas las dos del buzón personal.

## Comprobaciones y límites

- `./gradlew test`: 47 pruebas aprobadas, sin fallos, con H2 para la suite habitual.
- La migración V27 fue validada contra V1–V26 y aplicada a Neon después de crear la rama de resguardo `pre-v27-2026-10-31`.
- El DERCAS exige además clientes Android 12+, iOS 16+ y web, accesibilidad, manejo de desconexión y una medición de rendimiento (RNF-004/005/007–010). Esos entregables viven fuera de este repositorio; no se consideran verificados por la suite del backend.
- El despliegue actual no tiene SMTP, Google OAuth ni Supabase Storage configurados. Las rutas existen, pero los flujos que dependen de esos proveedores requieren credenciales y configuración del equipo. Los pagos siguen siendo simulados por requisito.
- La tecnología desplegada es Kotlin/Spring Boot en Cloud Run, mientras la sección 2.8 del DERCAS propone ASP.NET Core/Render. Es una diferencia de implementación, no una falta funcional de la API.
