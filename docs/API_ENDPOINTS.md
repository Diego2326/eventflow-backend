# Guía completa de endpoints de EventFlow

Esta guía describe las rutas HTTP del backend, sus entradas y el flujo principal. Base URL local: `http://localhost:5080`; despliegue: `https://eventflow-backend-990072178406.us-east4.run.app`. La raíz `/` redirige a `/swagger-ui`; el contrato interactivo está en `/v3/api-docs` y `/swagger-ui/index.html`.

## Convenciones

- `JWT + permiso` exige `Authorization: Bearer <accessToken>` y autorización por evento, rol o propiedad. `ADMIN` exige ese rol. Las rutas con token de invitación usan el token de la URL y no requieren cuenta, salvo `/link`, que además requiere JWT.
- Los cuerpos son JSON salvo subida de archivos. Los campos de cuerpo enumerados son los del esquema; consultar Swagger para tipos exactos y restricciones. `{eventId}`, `{id}` y otros segmentos entre llaves son variables de ruta.
- Los módulos del evento deben estar habilitados para crear acciones. Las operaciones sensibles validan capacidad, estado y duplicados. Los errores usan JSON `error.code`, `error.message`, `error.details`, `error.timestamp` y `error.path`.
- El pago y la compra de entradas son simulaciones académicas. La API no cobra dinero real ni almacena tarjetas.

## Flujos mínimos

1. `POST /api/auth/register`, verificar el enlace y hacer `POST /api/auth/login`; alternativamente usar Google con ID token firmado. Guardar el access token y el refresh token en almacenamiento seguro.
2. `POST /api/events`, consultar `GET /api/modules/catalog`, configurar con `PUT /api/events/{id}/modules/{code}` y publicar con `POST /api/events/{id}/status/PUBLISHED`.
3. Crear invitación, responder RSVP por token público, vincularla a una cuenta y consultar su experiencia/Event Pass. El personal registra ingreso con QR y permiso `CHECK_IN`.
4. Cargar agenda, datos de módulos y avisos. Las rutas de invitado solo exponen el evento y los módulos autorizados.

## Administración y reportes

Solo el rol ADMIN puede consultar colecciones, resolver reportes, decidir solicitudes de rol y activar o desactivar módulos globales. Las colecciones aceptan `page` y `size` opcionales.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/admin/audit` | Listar auditoría | `page`, `size` | - | 200 Lista de AuditLog | ADMIN |
| GET | `/api/admin/events` | Listar eventos | `page`, `size` | - | 200 Lista de EventEntity | ADMIN |
| PATCH | `/api/admin/modules/{code}` | Actualizar módulo global | - | CatalogUpdate: `enabled`, `name`, `description`, `category` | 200 ModuleCatalog | ADMIN |
| GET | `/api/admin/reports` | Listar reportes | `page`, `size` | - | 200 Lista de ModerationReport | ADMIN |
| POST | `/api/admin/reports/{id}/resolve` | Resolver reporte | - | ResolveReportRequest: `status`, `resolution` | 200 ModerationReport | ADMIN |
| GET | `/api/admin/role-requests` | Listar solicitudes de rol | `page`, `size` | - | 200 Lista de RoleRequest | ADMIN |
| POST | `/api/admin/role-requests/{id}/decision` | Decidir o solicitar rol | - | AdminDecision: `approved`, `reason` | 200 RoleRequest | ADMIN |
| GET | `/api/admin/users` | Listar usuarios | `page`, `size` | - | 200 Lista de AdminUserResponse | ADMIN |
| PATCH | `/api/admin/users/{id}/status` | Cambiar estado | - | UserStatusRequest: `status` | 200 User | ADMIN |
| POST | `/api/reports` | Reportar | - | ReportRequest: `eventId`, `targetType`, `targetId`, `reason` | 200 ModerationReport | JWT + permiso |

## Aforo

Expone ocupación y estado de zonas y servicios configurados para el evento.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/capacity/services` | Consultar servicios | - | - | 200 Lista de ServiceStateResponse | JWT + permiso |
| GET | `/api/events/{eventId}/capacity/zones` | Consultar zonas | - | - | 200 Lista de ZoneCapacityResponse | JWT + permiso |

## Agenda

El organizador edita actividades; el invitado consulta la agenda, ahora/siguiente y sus favoritas. Las actividades canceladas no aparecen como próximas.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/agenda` | Listar agenda | - | - | 200 Lista de AgendaItem | JWT + permiso |
| POST | `/api/events/{eventId}/agenda` | Agregar actividad | - | AgendaRequest: `title`, `description`, `startsAt`, `endsAt`, `zone`, `responsible`, `capacity` | 201 AgendaItem | JWT + permiso |
| GET | `/api/events/{eventId}/agenda/mine` | Listar propios | - | - | 200 Lista de AgendaItem | JWT + permiso |
| GET | `/api/events/{eventId}/agenda/now-next` | Ahora y siguiente | - | - | 200 NowNextResponse | JWT + permiso |
| DELETE | `/api/events/{eventId}/agenda/{id}` | Eliminar | - | - | 204 | JWT + permiso |
| PUT | `/api/events/{eventId}/agenda/{id}` | Actualizar | - | AgendaRequest: `title`, `description`, `startsAt`, `endsAt`, `zone`, `responsible`, `capacity` | 200 AgendaItem | JWT + permiso |
| PUT | `/api/events/{eventId}/agenda/{id}/favorite` | Marcar favorito | `enabled` | - | 204 | JWT + permiso |

## Archivos y galería

Sube archivos al almacenamiento de objetos, conserva metadatos en PostgreSQL y exige permisos de evento para listar, descargar, moderar o retirar contenido.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/files` | Listar | `module` | - | 200 Lista de FileResponse | JWT + permiso |
| POST | `/api/events/{eventId}/files/upload-url` | Autorizar carga directa a Neon (fotos GAL) | - | `module`, `fileName`, `contentType`, `sizeBytes`, `official` | 201 `fileId`, `uploadUrl`, `headers`, `expiresAt` | JWT + permiso |
| POST | `/api/events/{eventId}/files/{id}/complete-upload` | Verificar y activar carga directa | - | - | 200 FileResponse | JWT + dueño o permiso FILES |
| DELETE | `/api/events/{eventId}/files/{id}/upload` | Cancelar carga incompleta | - | - | 204 | JWT + dueño |
| POST | `/api/events/{eventId}/files` | Subir archivos RSC/SES/EXH | `module`, `recipientUserId`, `official` | multipart/form-data: object | 201 FileResponse | JWT + permiso |
| DELETE | `/api/events/{eventId}/files/{id}` | Retirar | - | - | 204 | JWT + permiso |
| GET | `/api/events/{eventId}/files/{id}` | Descargar | - | - | 200 string | JWT + permiso |
| PATCH | `/api/events/{eventId}/files/{id}/moderation` | Moderar | - | ModerationRequest: `status` | 200 FileResponse | JWT + permiso |

## Asistencia a sesiones

El personal autorizado marca asistencia individual a una sesión sin duplicarla.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| POST | `/api/events/{eventId}/sessions/{sessionId}/attendance/{invitationId}` | Registrar asistencia | - | - | 201 SessionAttendanceResponse | JWT + permiso |

## Asistencia durante el evento

El invitado crea solicitudes dentro de categorías habilitadas. El personal autorizado ve su bandeja y cambia el estado según transiciones válidas; primeros auxilios tiene prioridad.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/assistance` | Listar solicitudes | - | - | 200 Lista de AssistanceRequest | JWT + permiso |
| POST | `/api/events/{eventId}/assistance` | Crear solicitud | - | AssistanceRequestDto: `category`, `details`, `location`, `invitationId` | 201 AssistanceRequest | JWT + permiso |
| PATCH | `/api/events/{eventId}/assistance/{id}` | Actualizar solicitud | - | AssistanceUpdate: `status`, `assignedUserId` | 200 AssistanceRequest | JWT + permiso |

## Autenticación y sesiones

Registro y recuperación usan tokens de un solo uso. El login y Google devuelven JWT de acceso y refresh token; refresh rota la sesión. Para crear una cuenta nueva con Google se exigen teléfono y `acceptedTerms=true`; una cuenta ya existente puede iniciar sesión con su ID token verificado.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| POST | `/api/auth/cancel-deletion` | Cancelar eliminación | - | - | 204 | JWT + permiso |
| POST | `/api/auth/change-password` | Cambiar contraseña | - | ChangePasswordRequest: `currentPassword`, `newPassword`, `closeAllSessions` | 200 MessageResponse | JWT + permiso |
| POST | `/api/auth/confirm-email-change` | Confirmar correo | - | TokenRequest: `token` | 200 MessageResponse | Público |
| POST | `/api/auth/deactivate` | Desactivar cuenta | - | - | 204 | JWT + permiso |
| POST | `/api/auth/forgot-password` | Solicitar recuperación | - | ForgotPasswordRequest: `email` | 200 MessageResponse | Público |
| POST | `/api/auth/google` | Iniciar con Google | - | GoogleLoginRequest: `idToken`, `phonePrefixId`, `phoneNumber`, `acceptedTerms` | 200 AuthResponse | Público |
| POST | `/api/auth/login` | Iniciar sesión | - | LoginRequest: `identifier`, `password` | 200 AuthResponse | Público |
| POST | `/api/auth/logout` | Cerrar sesión | - | - | 204 | JWT + permiso |
| POST | `/api/auth/logout-all` | Cerrar todas las sesiones | - | - | 204 | JWT + permiso |
| POST | `/api/auth/refresh` | Renovar sesión | - | RefreshRequest: `refreshToken` | 200 AuthResponse | Público |
| POST | `/api/auth/register` | Registrar | - | RegisterRequest: `name`, `email`, `password`, `phonePrefixId`, `phoneNumber`, `phone`, `birthDate`, `nationalityCountryCode` | 201 MessageResponse | Público |
| POST | `/api/auth/request-deletion` | Solicitar eliminación | - | - | 204 | JWT + permiso |
| POST | `/api/auth/request-email-change` | Solicitar cambio de correo | - | EmailChangeRequest: `email` | 200 MessageResponse | JWT + permiso |
| POST | `/api/auth/resend-verification` | Reenviar verificación | - | ForgotPasswordRequest: `email` | 200 MessageResponse | Público |
| POST | `/api/auth/reset-password` | Restablecer contraseña | - | ResetPasswordRequest: `token`, `password` | 200 MessageResponse | Público |
| POST | `/api/auth/verify-email` | Verificar correo | - | TokenRequest: `token` | 200 MessageResponse | Público |

## Certificados

Publica certificados para asistentes elegibles y permite que cada invitado consulte únicamente los suyos.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| POST | `/api/events/{eventId}/certificates` | Emitir certificado | - | CertificateIssueRequest: `recipientUserId`, `fileId`, `title` | 201 CertificateResponse | JWT + permiso |
| GET | `/api/events/{eventId}/certificates/mine` | Listar propios | - | - | 200 Lista de CertificateResponse | JWT + permiso |

## Datos de módulos especializados

Motor común para MAP, ORD, QUE, BKG, INT, GAM, GAL, NET, EXH, SES, SPT, TRN, LNF, AFO, RSC y REV. `module` y `type` seleccionan el tipo de registro; `actions` registra JOIN, RESERVE, VOTE y otras acciones con reglas de capacidad y unicidad.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/module-data/map/my-location` | Consultar mi ubicación | - | - | 200 Lista de MapSearchResult | JWT + permiso |
| GET | `/api/events/{eventId}/module-data/map/search` | Buscar en mapa | `q` | - | 200 Lista de MapSearchResult | JWT + permiso |
| GET | `/api/events/{eventId}/module-data/my-resources` | Listar mis recursos | - | - | 200 Lista de ModuleRecordResponse | JWT + permiso |
| GET | `/api/events/{eventId}/module-data/networking/suggestions` | Sugerir perfiles | - | - | 200 Lista de ModuleRecordResponse | JWT + permiso |
| GET | `/api/events/{eventId}/module-data/queues/{id}/position` | Consultar turno | - | - | 200 QueuePositionResponse | JWT + permiso |
| DELETE | `/api/events/{eventId}/module-data/records/{id}` | Archivar | - | - | 204 | JWT + permiso |
| GET | `/api/events/{eventId}/module-data/records/{id}` | Consultar | - | - | 200 ModuleRecordResponse | JWT + permiso |
| PATCH | `/api/events/{eventId}/module-data/records/{id}` | Actualizar | - | ModuleRecordUpdate: `title`, `payload`, `status`, `capacity`, `startsAt`, `endsAt` | 200 ModuleRecordResponse | JWT + permiso |
| GET | `/api/events/{eventId}/module-data/records/{id}/actions` | Listar acciones | - | - | 200 Lista de ModuleActionResponse | JWT + permiso |
| POST | `/api/events/{eventId}/module-data/records/{id}/actions` | Registrar acción | - | ModuleActionRequest: `action`, `payload`, `unique`, `quantity` | 201 ModuleActionResponse | JWT + permiso |
| GET | `/api/events/{eventId}/module-data/records/{id}/poll-results` | Consultar votación | - | - | 200 PollResultsResponse | JWT + permiso |
| GET | `/api/events/{eventId}/module-data/records/{id}/trivia-results` | Consultar trivia | - | - | 200 TriviaResultsResponse | JWT + permiso |
| GET | `/api/events/{eventId}/module-data/{module}/{type}` | Listar | - | - | 200 Lista de ModuleRecordResponse | JWT + permiso |
| POST | `/api/events/{eventId}/module-data/{module}/{type}` | Crear | - | ModuleRecordRequest: `title`, `payload`, `status`, `capacity`, `startsAt`, `endsAt`, `parentRecordId` | 201 ModuleRecordResponse | JWT + permiso |

## Detalle de sesiones

Entrega sesión, ponente y materiales autorizados.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/sessions/{id}` | Consultar detalle | - | - | 200 SessionDetails | JWT + permiso |

## Dispositivos push

Registra, consulta y revoca tokens Expo de la cuenta autenticada. La entrega push depende de la configuración del proveedor.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/profile/push-devices` | Listar | - | - | 200 Lista de PushDeviceResponse | JWT + permiso |
| POST | `/api/profile/push-devices` | Registrar | - | RegisterPushDeviceRequest: `expoPushToken`, `platform` | 200 PushDeviceResponse | JWT + permiso |
| DELETE | `/api/profile/push-devices/{deviceId}` | Revocar | - | - | 204 | JWT + permiso |

## Eventos públicos y entradas simuladas

El catálogo público muestra eventos y tipos de entrada. La compra es simulada: genera pases, QR y comprobantes sin procesar tarjetas ni dinero real.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/ticket-orders` | Listar órdenes del evento | `page`, `size` | - | 200 Lista de TicketOrderResponse | JWT + permiso |
| POST | `/api/events/{eventId}/ticket-orders` | Comprar simulado | - | BuyTicketsRequest: `lines` | 201 TicketOrderResponse | JWT + permiso |
| POST | `/api/events/{eventId}/ticket-orders/{orderId}/cancel` | Cancelar orden | - | TicketRevokeRequest: `reason` | 200 TicketOrderResponse | JWT + permiso |
| GET | `/api/events/{eventId}/ticket-sales` | Consultar ventas simuladas | - | - | 200 TicketSalesResponse | JWT + permiso |
| GET | `/api/events/{eventId}/ticket-settings` | Consultar configuración | - | - | 200 TicketSettingsResponse | JWT + permiso |
| PUT | `/api/events/{eventId}/ticket-settings` | Consultar configuración | - | TicketSettingsRequest: `visibility`, `allowSalesWhileRunning`, `maxTicketsPerAccount`, `admissionCapacity`, … | 200 TicketSettingsResponse | JWT + permiso |
| GET | `/api/events/{eventId}/ticket-types` | Listar tipos de entrada | - | - | 200 Lista de TicketTypeResponse | JWT + permiso |
| POST | `/api/events/{eventId}/ticket-types` | Crear tipo de entrada | - | TicketTypeRequest: `name`, `description`, `price`, `currency`, `capacity`, `maxPerOrder`, `salesStart`, `salesEnd`, `enabled` | 201 TicketTypeResponse | JWT + permiso |
| PUT | `/api/events/{eventId}/ticket-types/{typeId}` | Editar tipo de entrada | - | TicketTypeRequest: `name`, `description`, `price`, `currency`, `capacity`, `maxPerOrder`, `salesStart`, `salesEnd`, `enabled` | 200 TicketTypeResponse | JWT + permiso |
| POST | `/api/events/{eventId}/tickets/{ticketId}/revoke` | Revocar | - | TicketRevokeRequest: `reason` | 200 TicketPassResponse | JWT + permiso |
| GET | `/api/public/events` | Consultar catálogo | `q`, `type`, `from`, `page`, `size` | - | 200 Lista de PublicEventResponse | Público |
| GET | `/api/public/events/{eventId}` | Consultar evento público | - | - | 200 PublicEventResponse | Público |
| GET | `/api/ticket-orders/mine` | Listar propios | - | - | 200 Lista de TicketOrderResponse | JWT + permiso |
| GET | `/api/ticket-orders/{orderId}` | Consultar mi orden | - | - | 200 TicketOrderResponse | JWT + permiso |
| POST | `/api/ticket-orders/{orderId}/cancel` | Cancelar | - | - | 200 TicketOrderResponse | JWT + permiso |
| POST | `/api/tickets/{ticketId}/regenerate` | Regenerar token | - | - | 200 TicketPassResponse | JWT + permiso |

## Eventos, módulos y colaboradores

El propietario crea y publica eventos, configura módulos, orden y destacados. Los colaboradores reciben permisos por evento. Los invitados solo ven módulos habilitados y visibles. El tipo de evento sugiere una plantilla, que se puede modificar.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events` | Listar | - | - | 200 Lista de EventResponse | JWT + permiso |
| POST | `/api/events` | Crear | - | CreateEventRequest: `name`, `type`, `startsAt`, `endsAt`, `timezone`, `location`, `estimatedCapacity`, `budget`, `description`, … | 201 EventResponse | JWT + permiso |
| GET | `/api/events/{id}` | Consultar | - | - | 200 EventResponse | JWT + permiso |
| PATCH | `/api/events/{id}` | Actualizar | - | UpdateEventRequest: `name`, `startsAt`, `endsAt`, `timezone`, `location`, `estimatedCapacity`, `budget`, `description`, … | 200 EventResponse | JWT + permiso |
| GET | `/api/events/{id}/collaborators` | Listar colaboradores | - | - | 200 Lista de EventCollaborator | JWT + permiso |
| POST | `/api/events/{id}/collaborators` | Agregar colaborador | - | CollaboratorRequest: `userId`, `permissions` | 201 EventCollaborator | JWT + permiso |
| DELETE | `/api/events/{id}/collaborators/{userId}` | Quitar colaborador | - | - | 204 | JWT + permiso |
| PATCH | `/api/events/{id}/collaborators/{userId}` | Editar colaborador | - | CollaboratorRequest: `userId`, `permissions` | 200 EventCollaborator | JWT + permiso |
| GET | `/api/events/{id}/dashboard` | Consultar panel | - | - | 200 DashboardResponse | JWT + permiso |
| GET | `/api/events/{id}/modules` | Listar módulos | - | - | 200 Lista de ModuleResponse | JWT + permiso |
| GET | `/api/events/{id}/modules/navigation` | Consultar navegación | - | - | 200 Lista de ModuleResponse | JWT + permiso |
| PUT | `/api/events/{id}/modules/{code}` | Configurar módulo | - | ConfigureModuleRequest: `enabled`, `order`, `featured`, `configuration` | 200 ModuleResponse | JWT + permiso |
| GET | `/api/events/{id}/permissions` | Consultar permisos | - | - | 200 EventPermissionResponse | JWT + permiso |
| POST | `/api/events/{id}/status/{status}` | Cambiar estado | - | - | 200 EventResponse | JWT + permiso |
| GET | `/api/modules/catalog` | Consultar catálogo | - | - | 200 Lista de ModuleResponse | JWT + permiso |

## Expositores

Expone el detalle autorizado de expositor, stand y ubicación.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/exhibitors/{id}` | Consultar detalle | - | - | 200 ExhibitorDetails | JWT + permiso |

## Gamificación

Consulta progreso, sincroniza misiones e inscribe hitos de pasaporte para invitados.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| POST | `/api/events/{eventId}/gamification/milestones/{milestoneId}/guests/{invitationId}` | Completar | - | - | 204 | JWT + permiso |
| GET | `/api/events/{eventId}/gamification/progress` | Consultar progreso | - | - | 200 GamificationResponse | JWT + permiso |
| POST | `/api/events/{eventId}/gamification/sync` | Sincronizar progreso | - | - | 200 GamificationResponse | JWT + permiso |

## Invitaciones, RSVP y acceso

El propietario genera tokens únicos para RSVP sin cuenta. Un invitado puede vincular el token a su cuenta y abrir el Event Pass. Check-in/out valida cupo, vigencia, reingreso y, opcionalmente, `memberIndex` para grupos.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| POST | `/api/events/{eventId}/check-in` | Registrar ingreso por QR | - | QrCheckRequest: `qrPayload`, `quantity`, `memberIndex` | 200 object | JWT + permiso |
| POST | `/api/events/{eventId}/check-out` | Registrar salida por QR | - | QrCheckRequest: `qrPayload`, `quantity`, `memberIndex` | 200 object | JWT + permiso |
| GET | `/api/events/{eventId}/invitations` | Listar | - | - | 200 Lista de InvitationResponse | JWT + permiso |
| POST | `/api/events/{eventId}/invitations` | Crear | - | CreateInvitationRequest: `guestName`, `guestEmail`, `allowedCapacity`, `expiresAt`, `table`, `seat`, `sector` | 201 InvitationResponse | JWT + permiso |
| POST | `/api/events/{eventId}/invitations/{id}/check-in` | Registrar ingreso | - | CheckRequest: `quantity`, `memberIndex` | 200 InvitationResponse | JWT + permiso |
| POST | `/api/events/{eventId}/invitations/{id}/check-out` | Registrar salida | - | CheckRequest: `quantity`, `memberIndex` | 200 InvitationResponse | JWT + permiso |
| POST | `/api/events/{eventId}/invitations/{id}/regenerate` | Regenerar token | - | - | 200 InvitationResponse | JWT + permiso |
| POST | `/api/events/{eventId}/invitations/{id}/revoke` | Revocar | - | - | 200 InvitationResponse | JWT + permiso |
| PATCH | `/api/events/{eventId}/invitations/{id}/seat` | Asignar asiento | - | SeatAssignmentRequest: `table`, `seat`, `sector` | 200 InvitationResponse | JWT + permiso |
| GET | `/api/invitations/access/{token}` | Consultar invitación | - | - | 200 InvitationResponse | Token invitación |
| GET | `/api/invitations/access/{token}/assistance` | Consultar solicitudes | - | - | 200 Lista de AssistanceRequest | Token invitación |
| POST | `/api/invitations/access/{token}/assistance` | Crear solicitud | - | GuestAssistanceRequest: `category`, `details`, `location` | 201 AssistanceRequest | Token invitación |
| GET | `/api/invitations/access/{token}/experience` | Consultar experiencia | - | - | 200 GuestExperienceResponse | Token invitación |
| POST | `/api/invitations/access/{token}/link` | Vincular invitación | - | - | 200 InvitationResponse | JWT + permiso |
| POST | `/api/invitations/access/{token}/rsvp` | Responder RSVP | - | RsvpRequest: `accepted`, `companions` | 200 InvitationResponse | Token invitación |
| POST | `/api/invitations/link` | Vincular invitaciones | - | LinkInvitationsRequest: `tokens` | 200 Lista de InvitationResponse | JWT + permiso |
| GET | `/api/invitations/mine` | Listar propios | - | - | 200 Lista de GuestExperienceResponse | JWT + permiso |
| GET | `/api/invitations/{id}/assistance` | Consultar solicitudes vinculadas | - | - | 200 Lista de AssistanceRequest | JWT + permiso |
| POST | `/api/invitations/{id}/assistance` | Crear solicitud vinculada | - | GuestAssistanceRequest: `category`, `details`, `location` | 201 AssistanceRequest | JWT + permiso |
| GET | `/api/invitations/{id}/experience` | Consultar experiencia vinculada | - | - | 200 GuestExperienceResponse | JWT + permiso |
| POST | `/api/invitations/{id}/rsvp` | Responder RSVP vinculado | - | RsvpRequest: `accepted`, `companions` | 200 InvitationResponse | JWT + permiso |

## Marketplace, reservas y pagos simulados

Espacios y servicios se publican y filtran por tipo, ubicación, precio, capacidad, características y disponibilidad. Una reserva pasa por decisión, pago simulado y cierre; solo una contratación concluida admite reseña.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/reservations` | Listar reservas | - | - | 200 Lista de Reservation | JWT + permiso |
| GET | `/api/offerings` | Buscar | `type`, `category`, `location`, `minCapacity`, `maxPrice`, `limit`, `minRating`, `feature`, `startsAt`, `endsAt` | - | 200 Lista de MarketplaceOffering | JWT + permiso |
| POST | `/api/offerings` | Crear | - | OfferingRequest: `type`, `name`, `category`, `description`, `location`, `capacity`, `price`, `attributes`, `imageUrls` | 201 MarketplaceOffering | JWT + permiso |
| PUT | `/api/offerings/{id}` | Actualizar | - | OfferingRequest: `type`, `name`, `category`, `description`, `location`, `capacity`, `price`, `attributes`, `imageUrls` | 200 MarketplaceOffering | JWT + permiso |
| GET | `/api/offerings/{id}/availability` | Consultar disponibilidad | - | - | 200 Lista de OfferingAvailability | JWT + permiso |
| POST | `/api/offerings/{id}/availability` | Consultar disponibilidad | - | AvailabilityRequest: `startsAt`, `endsAt`, `available` | 201 OfferingAvailability | JWT + permiso |
| POST | `/api/offerings/{id}/status/{status}` | Cambiar estado | - | - | 200 MarketplaceOffering | JWT + permiso |
| POST | `/api/reservations` | Reservar | - | ReservationRequest: `eventId`, `offeringId`, `startsAt`, `endsAt`, `note` | 201 Reservation | JWT + permiso |
| GET | `/api/reservations/mine` | Listar mis reservas | - | - | 200 Lista de Reservation | JWT + permiso |
| GET | `/api/reservations/{id}` | Consultar mi reserva | - | - | 200 Reservation | JWT + permiso |
| POST | `/api/reservations/{id}/cancel` | Cancelar | - | - | 200 Reservation | JWT + permiso |
| POST | `/api/reservations/{id}/complete` | Completar | - | - | 200 Reservation | JWT + permiso |
| POST | `/api/reservations/{id}/decision` | Decidir solicitud | - | DecisionRequest: `accepted` | 200 Reservation | JWT + permiso |
| GET | `/api/reservations/{id}/payments` | Listar comprobantes | - | - | 200 Lista de SimulatedPayment | JWT + permiso |
| POST | `/api/reservations/{id}/payments` | Registrar pago simulado | - | PaymentRequest: `amount`, `status` | 201 SimulatedPayment | JWT + permiso |
| GET | `/api/reservations/{id}/payments/{paymentId}/receipt` | Consultar comprobante | - | - | 200 SimulatedReceipt | JWT + permiso |
| POST | `/api/reservations/{id}/reviews` | Publicar reseña | - | ReviewRequest: `rating`, `comment` | 201 Review | JWT + permiso |

## Mensajería

Los mensajes quedan asociados al evento o reserva y solo los participantes autorizados ven el hilo. Para atención al invitado, `MSG.configuration.guestChannels` define canales abiertos (por defecto `STAFF`); `[]` los cierra. Los recibos de lectura se consultan por conversación.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/messages` | Listar mensajes | `channel` | - | 200 Lista de ConversationMessage | JWT + permiso |
| POST | `/api/events/{eventId}/messages` | Enviar mensaje | - | MessageRequest: `channel`, `body`, `recipientUserId`, `reservationId` | 201 ConversationMessage | JWT + permiso |
| GET | `/api/events/{eventId}/messages/conversations` | Listar conversaciones | `channel` | - | 200 Lista de ConversationSummary | JWT + permiso |
| GET | `/api/events/{eventId}/messages/conversations/{conversationId}` | Abrir conversación | `channel` | - | 200 Lista de ConversationMessage | JWT + permiso |
| POST | `/api/events/{eventId}/messages/conversations/{conversationId}/read` | Marcar lectura | `channel` | - | 200 MarkMessagesReadResponse | JWT + permiso |
| GET | `/api/events/{eventId}/messages/conversations/{conversationId}/read-state` | Consultar lecturas | `channel` | - | 200 Lista de MessageReadState | JWT + permiso |

## Notificaciones

El organizador publica avisos generales o segmentados; cada usuario consulta solo los aplicables a su evento, mesa, sector, sesión o transporte y puede marcarlos como leídos.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/notifications` | Listar avisos | - | - | 200 Lista de NotificationEntity | JWT + permiso |
| POST | `/api/events/{eventId}/notifications` | Enviar aviso | - | NotificationRequest: `title`, `body`, `audienceType`, `audienceValue`, `recipientUserId`, `channel` | 201 NotificationEntity | JWT + permiso |
| POST | `/api/events/{eventId}/notifications/read-all` | Marcar todos leídos | - | - | 200 object | JWT + permiso |
| GET | `/api/events/{eventId}/notifications/{id}` | Consultar aviso | - | - | 200 NotificationEntity | JWT + permiso |
| POST | `/api/events/{eventId}/notifications/{id}/read` | Marcar leído | - | - | 200 NotificationEntity | JWT + permiso |

## Perfil y preferencias

Todas las rutas usan la identidad del JWT. Las preferencias de notificación y privacidad pertenecen solo a esa cuenta; `export` entrega sus datos y `sessions` permite revocar sesiones. El buzón personal muestra avisos dirigidos a la cuenta, incluidos cambios de reservaciones, aunque `NOT` esté apagado.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/profile` | Consultar | - | - | 200 ProfileResponse | JWT + permiso |
| PATCH | `/api/profile` | Actualizar | - | UpdateProfileRequest: `name`, `phonePrefixId`, `phoneNumber`, `profilePictureUrl`, `birthDate`, `nationalityCountryCode` | 200 ProfileResponse | JWT + permiso |
| GET | `/api/profile/export` | Exportar datos propios | - | - | 200 AccountExport | JWT + permiso |
| GET | `/api/profile/notification-preferences` | Consultar preferencias | - | - | 200 Lista de NotificationPreference | JWT + permiso |
| PUT | `/api/profile/notification-preferences` | Guardar preferencia | - | PreferenceRequest: `channel`, `enabled` | 200 NotificationPreference | JWT + permiso |
| GET | `/api/profile/notifications` | Listar avisos personales | - | - | 200 Lista de NotificationEntity | JWT + permiso |
| POST | `/api/profile/notifications/{id}/read` | Marcar aviso propio como leído | - | - | 200 NotificationEntity | JWT + permiso |
| GET | `/api/profile/privacy` | Consultar privacidad | - | - | 200 PrivacyPreferencesResponse | JWT + permiso |
| PUT | `/api/profile/privacy` | Guardar privacidad | - | PrivacyPreferencesRequest: `networkingProfileVisible`, `networkingContactVisible` | 200 PrivacyPreferencesResponse | JWT + permiso |
| POST | `/api/profile/role-requests` | Decidir o solicitar rol | - | RoleRequestDto: `role`, `reason` | 201 RoleRequest | JWT + permiso |
| GET | `/api/profile/sessions` | Listar sesiones | - | - | 200 Lista de SessionResponse | JWT + permiso |
| DELETE | `/api/profile/sessions/{id}` | Revocar sesión | - | - | 204 | JWT + permiso |

## Plantillas propias de eventos

Plantillas privadas del organizador: guardar, editar, aplicar y eliminar combinaciones de módulos sin afectar otros eventos.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/event-templates` | Listar | - | - | 200 Lista de EventTemplateResponse | JWT + permiso |
| POST | `/api/event-templates` | Crear | - | EventTemplateRequest: `name`, `eventType`, `moduleCodes`, `description` | 201 EventTemplateResponse | JWT + permiso |
| POST | `/api/event-templates/apply/{templateId}` | Aplicar plantilla | - | ApplyEventTemplateRequest: `name`, `startsAt`, `endsAt`, `timezone`, `location`, `estimatedCapacity`, `budget`, `description`, … | 201 AppliedEventTemplateResponse | JWT + permiso |
| DELETE | `/api/event-templates/{templateId}` | Eliminar | - | - | 204 | JWT + permiso |
| GET | `/api/event-templates/{templateId}` | Consultar | - | - | 200 EventTemplateResponse | JWT + permiso |
| PUT | `/api/event-templates/{templateId}` | Actualizar | - | EventTemplateRequest: `name`, `eventType`, `moduleCodes`, `description` | 200 EventTemplateResponse | JWT + permiso |

## QR de networking

Comparte y consulta perfiles de networking respetando consentimiento y visibilidad de contacto.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/networking/profiles/{profileId}` | Consultar perfil por QR | - | - | 200 SharedProfileResponse | JWT + permiso |
| GET | `/api/events/{eventId}/networking/qr` | Obtener valor QR | - | - | 200 NetworkingQrResponse | JWT + permiso |

## Recursos compartidos

Permite abrir o guardar recursos del evento, sesión o expositor y obtener el valor codificable como QR.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/resources/{id}` | Consultar | - | - | 200 ModuleRecordResponse | JWT + permiso |
| GET | `/api/events/{eventId}/resources/{id}/qr` | Obtener valor QR | - | - | 200 ResourceQrResponse | JWT + permiso |
| POST | `/api/events/{eventId}/resources/{id}/save` | Guardar | - | - | 201 ModuleActionResponse | JWT + permiso |

## Resultados deportivos

Confirma un marcador y actualiza el ganador y la siguiente ronda del bracket.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| POST | `/api/events/{eventId}/matches/{matchId}/result` | Reportar | - | MatchResultRequest: `scoreA`, `scoreB` | 200 MatchResultResponse | JWT + permiso |

## Reuniones de networking

Solicita, acepta, rechaza y cancela reuniones; la aceptación comprueba conflictos de horario.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/networking/meetings` | Listar propios | - | - | 200 Lista de MeetingResponse | JWT + permiso |
| POST | `/api/events/{eventId}/networking/meetings` | Solicitar | - | MeetingRequest: `participantUserId`, `startsAt`, `endsAt`, `location` | 201 MeetingResponse | JWT + permiso |
| POST | `/api/events/{eventId}/networking/meetings/{id}/accept` | Aceptar | - | - | 200 MeetingResponse | JWT + permiso |
| POST | `/api/events/{eventId}/networking/meetings/{id}/cancel` | Cancelar | - | - | 200 MeetingResponse | JWT + permiso |
| POST | `/api/events/{eventId}/networking/meetings/{id}/decline` | Rechazar | - | - | 200 MeetingResponse | JWT + permiso |

## Rutas de transporte

Entrega ruta y salidas activas autorizadas para el invitado.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| GET | `/api/events/{eventId}/transport/routes/{id}` | Consultar detalle | - | - | 200 RouteDetails | JWT + permiso |

## Sorteos

Ejecuta un sorteo entre invitados elegibles; las reglas pueden excluir ganadores previos.

| Método | Ruta | Qué hace | Consulta | Cuerpo | Respuesta | Acceso |
|---|---|---|---|---|---|---|
| POST | `/api/events/{eventId}/draws/{drawId}/run` | Ejecutar sorteo | - | - | 200 DrawWinnerResponse | JWT + permiso |

## WebSocket

`/ws` usa STOMP sobre WebSocket. El cliente autenticado se suscribe a `/user/queue/events/{eventId}/messages` y envía a `/app/events/{eventId}/messages`. El backend valida sesión y pertenencia al evento en cada mensaje.

## Referencias de uso detallado

Los ejemplos de payload y reglas por módulo están en `docs/frontend/README.md` y sus guías enlazadas. La tabla incluye las 177 operaciones de la versión publicada de OpenAPI y las dos rutas nuevas del buzón personal, para un total de 179.
