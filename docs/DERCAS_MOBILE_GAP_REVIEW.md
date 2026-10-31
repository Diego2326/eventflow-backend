# Revisión de la app móvil frente al DERCAS v2.0

Revisión de `eventflow-app` y del contrato REST de este repositorio, 2026-10-31. El DERCAS v2.0 define 116 requisitos funcionales y también requisitos no funcionales para Android, iOS y web. Esta app es solo el cliente móvil; no hay un cliente web en el workspace.

## Lo que ya existe

- Registro, inicio de sesión, Google Sign-In, verificación y recuperación de cuenta.
- Inicio con eventos, agenda, RSVP, notificaciones, perfil, privacidad y pases.
- Adaptadores de modo demo local y almacenamiento seguro de sesión.
- Pase digital expandible con QR real, estados de acceso y explicación para el invitado.
- Apertura de invitaciones mediante `eventflow://invite/{token}`, con agenda permitida, RSVP público, acompañantes y opción para vincular a una cuenta con sesión.
- Navegación de módulos obtenida del evento, con accesos a agenda, pases y notificaciones.
- Agenda personal con favoritos, recordatorios locales 15 minutos antes y acceso para agregar actividades al calendario del dispositivo.
- Mapa con búsqueda por lugares publicados y ubicación de mesa/sector asignada.
- Networking voluntario con consentimiento, campos de perfil, sugerencias y opción de ocultar el perfil.
- Galería compartida con selección o captura de fotos, compresión antes de subir, revisión por permisos y filtro de galería oficial.
- Solicitudes de ayuda de asistentes y cola para personal con asignación/avance/cierre de casos.
- Quien todavía no tiene cuenta también puede pedir ayuda desde el enlace personal de invitación.
- Centro operativo con escaneo de pases QR, búsqueda de invitados y check-in/check-out agregado o por integrante cuando el registro ya usa seguimiento individual.
- Copia segura del pase digital disponible durante una interrupción de red, identificada como desactualizable; la validación de entrada sigue siendo en línea.
- Los permisos del evento controlan qué herramientas de personal aparecen; la API sigue validando cada acción.
- Flujos móviles conectados para filas, reservas de actividades, encuestas, trivia, preguntas, menú, pedidos y cancelación de pedidos pendientes. El pedido usa el total calculado por la API.
- En este cambio se corrigieron las rutas REST de perfil y notificaciones, los cuerpos de registro/recuperación/Google, y la lectura de RSVP, agenda, pases y capacidad para que usen contratos implementados por la API.

## Pendiente para completar el alcance móvil

| Prioridad | Entregable | Brecha observada |
|---|---|---|
| Alta | Entrada por invitación | La app ya abre el enlace de invitación y permite consultar la experiencia, responder RSVP sin cuenta y vincularla si ya hay sesión. Falta configurar enlaces universales HTTPS para el dominio público cuando se defina. |
| Alta | Event Pass | El invitado puede mostrar el QR; el personal puede escanearlo y registrar entrada/salida. |
| Alta | Organización del evento | Siguen pendientes las pantallas de propietario para crear/editar/publicar eventos, asignar mesas y configurar módulos. La lista de invitados y el check-in operativo ya están disponibles para roles autorizados. |
| Alta | Navegación modular | La pantalla del evento ahora muestra los módulos habilitados y destacados que devuelve la API; agenda, pases y notificaciones abren sus flujos existentes. |
| Alta | Módulos de ejecución | Además de filas, reservas, encuestas, preguntas, menú y pedidos, ya hay flujos de agenda personal, búsqueda de mapa, networking con consentimiento, galería con moderación y solicitudes de ayuda. Faltan experiencias especializadas de gamificación, deportes, transporte, objetos perdidos, aforo, recursos, reseñas y mensajería. |
| Media | Push remoto | La app implementa recordatorios locales de agenda. La API registra dispositivos Expo, pero la app todavía no registra/revoca tokens push remotos ni configura credenciales EAS. |
| Media | Términos y privacidad | Google conserva el registro de aceptación de términos en la API; la aceptación de aviso de privacidad que muestra la app no se envía ni se guarda en backend. |
| Media | Presentación de datos | Los módulos incorporados muestran carga, errores, contenido vacío y actualización por gesto. La app conserva una copia segura del pase; siguen pendientes paginación y estados sin conexión de los demás flujos, además de validación de uso real en dispositivos. |
| Validación | Plataformas y calidad | No se documenta validación visual ni funcional en Android 12+ e iOS 16+, accesibilidad de todos los módulos o medición de rendimiento desde este workspace. |

La API ya expone rutas para la mayoría de estos módulos según [DERCAS_GAP_REVIEW.md](DERCAS_GAP_REVIEW.md) y [FRONTEND_API_HANDOFF.md](FRONTEND_API_HANDOFF.md). Las brechas de esta tabla distinguen los flujos móviles implementados de los contratos que todavía no tienen una experiencia completa.

## Cloud Run

El backend tiene `Dockerfile` y respeta el puerto `PORT` que Cloud Run inyecta. La configuración activa del servicio, sus secretos y el esquema real de la base desplegada no están disponibles en los repositorios; hay que comprobarlos en Google Cloud antes de publicar una nueva imagen. La lista de configuración requerida está en [cloud-run-deployment.md](cloud-run-deployment.md).
