# EventFlow REST endpoint catalog

Derived from controller mappings in `src/main/kotlin` for this backend revision. Dynamic identifiers use `{name}` path parameters. The five admin collection GET routes accept optional `page`/`size` query parameters and retain the legacy list response when omitted. WebSocket/STOMP is documented separately in `FRONTEND_API_HANDOFF.md`.

## AdminController

- `POST /api/reports`
- `GET /api/admin/users`
- `GET /api/admin/events`
- `GET /api/admin/audit`
- `PATCH /api/admin/users/{id}/status`
- `GET /api/admin/role-requests`
- `POST /api/admin/role-requests/{id}/decision`
- `GET /api/admin/reports`
- `POST /api/admin/reports/{id}/resolve`
- `PATCH /api/admin/modules/{code}`

## AgendaController

- `POST /api/events/{eventId}/agenda`
- `GET /api/events/{eventId}/agenda`
- `GET /api/events/{eventId}/agenda/now-next`
- `GET /api/events/{eventId}/agenda/mine`
- `PUT /api/events/{eventId}/agenda/{id}`
- `DELETE /api/events/{eventId}/agenda/{id}`
- `PUT /api/events/{eventId}/agenda/{id}/favorite`

## AssistanceController

- `POST /api/events/{eventId}/assistance`
- `GET /api/events/{eventId}/assistance`
- `PATCH /api/events/{eventId}/assistance/{id}`

## AuthController

- `POST /api/auth/register`
- `POST /api/auth/verify-email`
- `POST /api/auth/resend-verification`
- `POST /api/auth/login`
- `POST /api/auth/google`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/logout-all`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/auth/change-password`
- `POST /api/auth/deactivate`
- `POST /api/auth/request-deletion`
- `POST /api/auth/cancel-deletion`
- `POST /api/auth/request-email-change`
- `POST /api/auth/confirm-email-change`

## CapacityController

- `GET /api/events/{eventId}/capacity/zones`
- `GET /api/events/{eventId}/capacity/services`

## EventController

- `GET /api/events`
- `POST /api/events`
- `GET /api/events/{id}`
- `PATCH /api/events/{id}`
- `POST /api/events/{id}/status/{status}`
- `GET /api/events/{id}/dashboard`
- `GET /api/modules/catalog`
- `GET /api/events/{id}/modules`
- `GET /api/events/{id}/modules/navigation`
- `PUT /api/events/{id}/modules/{code}`
- `POST /api/events/{id}/collaborators`
- `GET /api/events/{id}/collaborators`
- `GET /api/events/{id}/permissions`
- `PATCH /api/events/{id}/collaborators/{userId}`
- `DELETE /api/events/{id}/collaborators/{userId}`

## ExhibitionController

- `GET /api/events/{eventId}/exhibitors/{id}`

## GamificationController

- `GET /api/events/{eventId}/gamification/progress`
- `POST /api/events/{eventId}/gamification/sync`
- `POST /api/events/{eventId}/gamification/milestones/{milestoneId}/guests/{invitationId}`

## DrawController

- `POST /api/events/{eventId}/draws/{drawId}/run`

## InvitationController

- `POST /api/events/{eventId}/invitations`
- `GET /api/events/{eventId}/invitations`
- `POST /api/events/{eventId}/invitations/{id}/revoke`
- `POST /api/events/{eventId}/invitations/{id}/regenerate`
- `PATCH /api/events/{eventId}/invitations/{id}/seat`
- `POST /api/events/{eventId}/invitations/{id}/check-in`
- `POST /api/events/{eventId}/invitations/{id}/check-out`
- `POST /api/events/{eventId}/check-in`
- `POST /api/events/{eventId}/check-out`
- `GET /api/invitations/access/{token}`
- `GET /api/invitations/access/{token}/experience`
- `POST /api/invitations/access/{token}/rsvp`
- `POST /api/invitations/access/{token}/assistance`
- `GET /api/invitations/access/{token}/assistance`
- `POST /api/invitations/access/{token}/link`
- `POST /api/invitations/link`
- `GET /api/invitations/mine`
- `GET /api/invitations/{id}/experience`
- `POST /api/invitations/{id}/rsvp`
- `POST /api/invitations/{id}/assistance`
- `GET /api/invitations/{id}/assistance`

## MarketplaceController

- `POST /api/offerings`
- `PUT /api/offerings/{id}`
- `POST /api/offerings/{id}/status/{status}`
- `GET /api/offerings`
- `POST /api/offerings/{id}/availability`
- `GET /api/offerings/{id}/availability`
- `POST /api/reservations`
- `GET /api/reservations/mine`
- `GET /api/reservations/{id}`
- `GET /api/events/{eventId}/reservations`
- `POST /api/reservations/{id}/decision`
- `POST /api/reservations/{id}/cancel`
- `POST /api/reservations/{id}/payments`
- `GET /api/reservations/{id}/payments`
- `GET /api/reservations/{id}/payments/{paymentId}/receipt`
- `POST /api/reservations/{id}/complete`
- `POST /api/reservations/{id}/reviews`

## MessagingController

- `POST /api/events/{eventId}/messages`
- `GET /api/events/{eventId}/messages`
- `GET /api/events/{eventId}/messages/conversations`
- `GET /api/events/{eventId}/messages/conversations/{conversationId}`

## ModuleDataController

- `POST /api/events/{eventId}/module-data/{module}/{type}`
- `GET /api/events/{eventId}/module-data/{module}/{type}`
- `GET /api/events/{eventId}/module-data/records/{id}`
- `PATCH /api/events/{eventId}/module-data/records/{id}`
- `DELETE /api/events/{eventId}/module-data/records/{id}`
- `POST /api/events/{eventId}/module-data/records/{id}/actions`
- `GET /api/events/{eventId}/module-data/records/{id}/actions`
- `GET /api/events/{eventId}/module-data/records/{id}/poll-results`
- `GET /api/events/{eventId}/module-data/records/{id}/trivia-results`
- `GET /api/events/{eventId}/module-data/map/search`
- `GET /api/events/{eventId}/module-data/map/my-location`
- `GET /api/events/{eventId}/module-data/queues/{id}/position`
- `GET /api/events/{eventId}/module-data/networking/suggestions`
- `GET /api/events/{eventId}/module-data/my-resources`

## MeetingController

- `POST /api/events/{eventId}/networking/meetings`
- `GET /api/events/{eventId}/networking/meetings`
- `POST /api/events/{eventId}/networking/meetings/{id}/accept`
- `POST /api/events/{eventId}/networking/meetings/{id}/decline`
- `POST /api/events/{eventId}/networking/meetings/{id}/cancel`

## NetworkingQrController

- `GET /api/events/{eventId}/networking/qr`
- `GET /api/events/{eventId}/networking/profiles/{profileId}`

## NotificationController

- `POST /api/events/{eventId}/notifications`
- `GET /api/events/{eventId}/notifications`
- `GET /api/events/{eventId}/notifications/{id}`
- `POST /api/events/{eventId}/notifications/{id}/read`
- `POST /api/events/{eventId}/notifications/read-all`

## PushDeviceController

- `POST /api/profile/push-devices`
- `GET /api/profile/push-devices`
- `DELETE /api/profile/push-devices/{deviceId}`

## CertificateController

- `POST /api/events/{eventId}/certificates`
- `GET /api/events/{eventId}/certificates/mine`

## ResourceSharingController

- `GET /api/events/{eventId}/resources/{id}/qr`
- `GET /api/events/{eventId}/resources/{id}`
- `POST /api/events/{eventId}/resources/{id}/save`

## SessionAttendanceController

- `POST /api/events/{eventId}/sessions/{sessionId}/attendance/{invitationId}`

## SessionDetailsController

- `GET /api/events/{eventId}/sessions/{id}`

## MatchResultController

- `POST /api/events/{eventId}/matches/{matchId}/result`

## StorageController

- `POST /api/events/{eventId}/files`
- `GET /api/events/{eventId}/files`
- `GET /api/events/{eventId}/files/{id}`
- `PATCH /api/events/{eventId}/files/{id}/moderation`
- `DELETE /api/events/{eventId}/files/{id}`

## TicketingController

- `GET /api/public/events`
- `GET /api/public/events/{eventId}`
- `GET /api/events/{eventId}/ticket-settings`
- `PUT /api/events/{eventId}/ticket-settings`
- `POST /api/events/{eventId}/ticket-types`
- `PUT /api/events/{eventId}/ticket-types/{typeId}`
- `GET /api/events/{eventId}/ticket-types`
- `POST /api/events/{eventId}/ticket-orders`
- `GET /api/ticket-orders/mine`
- `GET /api/ticket-orders/{orderId}`
- `POST /api/ticket-orders/{orderId}/cancel`
- `GET /api/events/{eventId}/ticket-orders`
- `POST /api/events/{eventId}/ticket-orders/{orderId}/cancel`
- `GET /api/events/{eventId}/ticket-sales`
- `POST /api/events/{eventId}/tickets/{ticketId}/revoke`
- `POST /api/tickets/{ticketId}/regenerate`

## TransportRouteController

- `GET /api/events/{eventId}/transport/routes/{id}`

## UserController

- `GET /api/profile`
- `PATCH /api/profile`
- `GET /api/profile/notification-preferences`
- `PUT /api/profile/notification-preferences`
- `POST /api/profile/role-requests`
- `GET /api/profile/sessions`
- `GET /api/profile/export`
- `DELETE /api/profile/sessions/{id}`


## EventTemplateController

- `GET /api/event-templates`
- `GET /api/event-templates/{templateId}`
- `POST /api/event-templates`
- `PUT /api/event-templates/{templateId}`
- `DELETE /api/event-templates/{templateId}`
- `POST /api/event-templates/apply/{templateId}`

## UserController additions

- `GET /api/profile/privacy`
- `PUT /api/profile/privacy`

## MessagingController additions

- `POST /api/events/{eventId}/messages/conversations/{conversationId}/read`
- `GET /api/events/{eventId}/messages/conversations/{conversationId}/read-state`