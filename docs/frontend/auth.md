# AUT / USR: autenticación y perfil

## Alta e inicio de sesión

| Método y ruta | Cuerpo | Uso |
|---|---|---|
| `POST /api/auth/register` | Datos de `RegisterRequest` (ver OpenAPI) | Crea cuenta pendiente de verificación. |
| `POST /api/auth/verify-email` | `{ "token": "..." }` | Activa correo. |
| `POST /api/auth/resend-verification` | `{ "email": "..." }` | Reenvía enlace. |
| `POST /api/auth/login` | `{ "identifier": "correo o teléfono", "password": "..." }` | Devuelve `AuthResponse`. |
| `POST /api/auth/google` | `{ "idToken": "..." }` | Inicia sesión con token de Google. |
| `POST /api/auth/refresh` | `{ "refreshToken": "..." }` | Rota el refresh token; guarda el nuevo. |

`AuthResponse` contiene `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `userId`, `name`, `email` y `roles`. Guarda el refresh token en almacenamiento protegido de la plataforma; no lo incluyas en URLs. `POST /api/auth/logout` cierra la sesión actual y `/logout-all` cierra todas.

## Recuperación y cambios

- `POST /api/auth/forgot-password` con `{ "email": "..." }`; `POST /api/auth/reset-password` con `{ "token": "...", "password": "..." }`.
- `POST /api/auth/change-password` con `currentPassword`, `newPassword` y `closeAllSessions` opcional.
- `POST /api/auth/request-email-change` con `{ "email": "..." }`; confirma mediante `/confirm-email-change` con `{ "token": "..." }` y vuelve a iniciar sesión.
- `POST /api/auth/deactivate`, `/request-deletion` y `/cancel-deletion` gestionan la cuenta. Las respuestas con `developmentToken` solo aparecen si el servidor habilitó explícitamente el modo de desarrollo.

## Perfil

`GET/PATCH /api/profile` consulta o actualiza `name`, teléfono, foto, nacimiento y nacionalidad. `GET /api/profile/notification-preferences` y `PUT` con `{ "channel": "IN_APP", "enabled": true }` controlan avisos. `POST /api/profile/role-requests` con `{ "role": "...", "reason": "..." }` solicita roles de organizador, propietario o proveedor; la aprobación administrativa es necesaria antes de usar las rutas con esos roles.
