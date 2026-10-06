# Eventos públicos y entradas simuladas

Estado: **implementado en backend**. Fecha: 2026-10-06.

## Objetivo y relación con el DERCAS

Permitir que una persona descubra un evento público, adquiera entradas de forma **simulada**, consulte sus pases y acceda al evento mediante QR. Una convención con entradas General y VIP es el caso de referencia.

Esta función amplía el DERCAS v2.0: el documento contempla invitaciones, Event Pass, check-in, aforo y pagos simulados de reservaciones, pero no define venta pública de entradas. Conserva sus restricciones generales: no procesar dinero ni tarjetas reales, aislar los datos por evento y evitar duplicados en cupos y accesos. Las invitaciones privadas siguen siendo válidas para el mismo evento.

## Decisiones acordadas

- Comprar o reservar una entrada gratuita exige una cuenta de EventFlow autenticada. El catálogo y la ficha pública se pueden consultar sin iniciar sesión.
- La compra simulada se **acepta y confirma inmediatamente**; no hay pasarela, captura de tarjeta, aprobación externa ni estados de pago pendiente o fallido en esta versión.
- Los pases quedan asociados a la cuenta compradora y aparecen en «Mis entradas». Cada entrada genera un pase individual.
- Un evento privado no aparece en el catálogo ni admite compras públicas. Un evento público puede conservar invitaciones privadas para invitados especiales.
- La interfaz debe indicar claramente «Compra simulada — no se realizó ningún cobro» en confirmación, historial y comprobante.
- El organizador configura por evento la venta durante el evento, los límites por cuenta, el aforo de venta y la posibilidad de revocar pases ya usados. Los valores iniciales y las restricciones están en EP-02 y EP-06.

## Actores

| Actor | Capacidades |
| --- | --- |
| Visitante | Explorar y consultar eventos públicos publicados. |
| Usuario registrado | Comprar entradas simuladas, ver pases y consultar sus compras. |
| Organizador | Publicar el evento, definir tipos y cupos, consultar ventas y anular entradas cuando corresponda. |
| Personal con permiso de check-in | Escanear pases, registrar entrada y salida conforme a la política del evento. |

## Requisitos funcionales

### EP-01. Publicación y descubrimiento

- El organizador elige visibilidad `PRIVATE` o `PUBLIC`; por defecto, `PRIVATE` para preservar el comportamiento actual.
- Solo los eventos `PUBLIC` en estado `PUBLISHED` o `RUNNING` aparecen en el catálogo público. Un evento `DRAFT`, `CANCELLED` o `FINISHED` no se ofrece para compra. Una compra existente sigue visible en «Mis entradas» después de finalizar o cancelar el evento.
- El catálogo ofrece paginación y filtros por texto, fecha y tipo. La ficha muestra nombre, descripción, fecha/hora con zona, ubicación, organizador visible, tipos de entrada y disponibilidad, sin datos privados del organizador ni de asistentes.
- Cambiar de `PUBLIC` a `PRIVATE` después de emitir entradas requiere una regla explícita; para la primera versión se rechaza mientras existan pases activos.

### EP-02. Tipos de entrada

- El organizador crea tipos como General o VIP para un evento propio. Cada tipo tiene nombre, descripción opcional, precio simulado no negativo, moneda, cupo entero positivo, máximo por compra, inicio y fin de venta, y estado habilitado.
- Precio `0` significa registro gratuito y recorre el mismo flujo de compra y pase. Los montos se manejan como decimales, sin cálculos de punto flotante.
- La ventana de venta debe estar dentro de fechas coherentes con el evento. La opción `allowSalesWhileRunning` es configurable por evento y vale `false` por defecto; si vale `true`, se puede vender durante `RUNNING` hasta la hora de cierre configurada, nunca después de finalizar o cancelar el evento. No se vende antes de abrir ventas ni al agotarse el cupo.
- `maxTicketsPerAccount` limita la suma de pases activos adquiridos por una cuenta en el evento, incluso en pedidos diferentes. Es configurable por evento y, por defecto, vale 4; cada tipo conserva además su máximo por compra. Los pases cancelados antes de usarse no cuentan para este límite.
- El organizador puede definir `admissionCapacity` como límite duro total de accesos del evento; si lo deja vacío, `estimatedCapacity` sigue siendo solo una estimación y los límites obligatorios son los cupos de cada tipo. El organizador también configura `reservedInvitationCapacity` para invitados privados, con valor inicial 0.
- Cuando hay límite duro, la venta pública disponible no supera `admissionCapacity` menos el mayor entre la reserva privada y las plazas comprometidas por invitaciones no revocadas, menos las entradas activas o ya utilizadas. Nuevas invitaciones tampoco pueden exceder ese límite. La reserva privada puede modificarse si no perjudica compromisos existentes.
- No se pueden bajar cupos, límites de cuenta ni aforo por debajo de entradas o invitaciones ya comprometidas. El organizador ve emitidos y disponibles por tipo y para el evento completo.
- Cambios de precio, nombre o descripción no alteran el detalle histórico de compras ya confirmadas.

### EP-03. Compra simulada

- El usuario autenticado elige uno o más tipos y cantidades positivas. La primera versión limita la compra a un solo evento por pedido.
- Al confirmar, el backend recalcula precios y disponibilidad. El cliente no envía el total como fuente de verdad.
- La compra se confirma en una sola transacción: crea pedido, detalle, referencia/comprobante simulado y exactamente un pase por entrada. Si falla cualquier paso, no se emiten pases ni se consumen cupos.
- El backend usa una clave de idempotencia por intento de compra. Repetir el mismo intento devuelve el mismo pedido; reutilizar la clave con otro contenido se rechaza. Las compras concurrentes no pueden sobrepasar el cupo del tipo, el máximo por cuenta ni el aforo duro configurado.
- El pedido guarda precios unitarios, moneda, total, comprador y fecha como instantánea histórica. Se registra explícitamente que la operación fue simulada.
- Una compra puede incluir varias entradas. Todas quedan bajo la cuenta compradora; el pase muestra un número legible dentro del pedido para distinguirlos. En esta versión no hay transferencia ni asignación de titular diferente.

### EP-04. Mis entradas y pase

- El comprador consulta sus pedidos y pases en «Mis entradas», incluyendo eventos futuros y anteriores. Otra cuenta no puede consultarlos ni usarlos mediante el ID del recurso.
- Cada pase presenta evento, tipo, fecha, ubicación, estado y un valor QR opaco generado por el backend. El cliente dibuja el QR; el identificador de base de datos por sí solo no sirve como credencial.
- El valor QR no se expone en catálogos, listados públicos ni paneles de ventas. Puede regenerarse por una operación autorizada, invalidando el anterior.
- La compra da acceso a la experiencia del evento y a los módulos visibles para asistentes, sin requerir una invitación adicional. Las reglas de contenido privado de cada módulo siguen aplicando.

### EP-05. Acceso y reingreso

- El personal autorizado escanea un QR de entrada y recibe un resultado inequívoco: válido, ya utilizado, anulado, evento equivocado, fuera de vigencia o código inválido.
- Cada pase admite un check-in a la vez. Dos escaneos simultáneos no producen dos ingresos. Se guardan pase, evento, acción, fecha/hora y operador.
- Si `reentryAllowed=false`, un pase que ya ingresó no puede volver a entrar aunque haya registrado salida. Si es `true`, la salida habilita otro ingreso y el historial permanece.
- El lector del evento acepta invitaciones y entradas compradas, pero aplica a cada una sus propias reglas. Un pase de un evento nunca abre otro.
- La ocupación y el panel del evento distinguen invitaciones, entradas emitidas e ingresos efectivos; no suman dos veces la misma persona.

### EP-06. Anulación y cancelación

- El organizador puede anular un pase o un pedido antes del acceso, con motivo registrado. La anulación invalida inmediatamente el QR y libera el cupo de venta de los pases anulados.
- El comprador puede cancelar un pedido completo antes del inicio del evento y antes de usar cualquier pase; la primera versión no admite cancelación parcial ni reembolso real. La interfaz explica que solo se revierte una compra simulada.
- `allowRevokeAfterCheckIn` es configurable por evento y vale `false` por defecto. Si se activa, el organizador puede **revocar** un pase ya utilizado con motivo; esto invalida el QR para nuevos ingresos y conserva todos los registros de acceso. Un pase revocado después del primer ingreso no libera cupo de venta ni se considera una cancelación del pedido.
- Si el titular revocado está dentro, su ocupación continúa contabilizada hasta que el personal registre la salida. La revocación no registra una salida ficticia.
- La cancelación del evento impide nuevos accesos y ventas, pero conserva pedidos e historial con estado visible para el comprador.
- Las anulaciones, revocaciones y cancelaciones deben ser idempotentes; nunca liberan cupo dos veces.

### EP-07. Gestión del organizador

- El organizador consulta pedidos, pases, conteos por tipo, total simulado e ingresos. Los listados son paginados y se filtran siempre por evento.
- El panel diferencia explícitamente ingresos simulados de dinero real. Solo propietario o colaboradores con permiso adecuado pueden gestionar entradas; solo personal con `CHECK_IN` puede registrar accesos.
- Una entrada gratuita cuenta en el aforo y en el total de pases, pero suma cero al ingreso simulado.

## Estados propuestos

| Recurso | Estados | Transiciones permitidas |
| --- | --- | --- |
| Pedido | `CONFIRMED`, `CANCELLED` | Se crea confirmado; puede cancelarse bajo EP-06. |
| Pase | `ACTIVE`, `CANCELLED`, `REVOKED` | Se crea activo; puede anularse antes de usarlo o revocarse después del primer ingreso si la política lo permite. Su presencia se deriva del historial de check-in/out, no de un estado mutable adicional. |
| Tipo de entrada | `ENABLED`, `DISABLED` | Deshabilitar impide nuevas ventas, conserva compras existentes. |

El recibo conserva el total original. Al cancelar se registra la reversión simulada separadamente para que el historial sea auditable.

## Criterios de aceptación de punta a punta

1. Un visitante ve una convención pública publicada con General y VIP, pero no ve eventos privados ni borradores.
2. Al iniciar sesión y comprar dos entradas General, recibe un pedido confirmado, un comprobante identificado como simulado y dos QR distintos en «Mis entradas».
3. Al repetir la solicitud con la misma clave de idempotencia no se crean otro pedido ni otros pases.
4. Con una sola plaza disponible, dos compras simultáneas no pueden confirmar dos pases.
5. Un escáner autorizado admite el primer ingreso y rechaza el duplicado; otro evento o un operador sin permiso no puede validar el pase.
6. Un pase cancelado antes de usarse deja de ser válido y su plaza vuelve a estar disponible exactamente una vez. Un pase revocado después del ingreso no libera plaza ni borra la asistencia.
7. Una cuenta ajena no puede listar ni consultar el pedido o sus códigos QR.
8. El comprador puede entrar a los módulos de asistente permitidos sin crear una invitación artificial.
9. El evento privado existente conserva su flujo de invitación, RSVP y check-in.
10. El organizador puede activar la venta durante `RUNNING`, cambiar el máximo por cuenta, fijar aforo duro y reserva para invitaciones, y autorizar revocaciones posteriores al ingreso; cada cambio respeta los compromisos existentes.

## Integración prevista

Ubicación propuesta: `ticketing/domain`, `ticketing/application` y `ticketing/infrastructure`, siguiendo la organización por módulos del repositorio. `event` incorpora visibilidad y consultas públicas; `invitation` conserva sus invitaciones. El acceso por QR se coordina desde una operación común o adaptador que reconoce el origen del pase. El pago simulado de `marketplace` sigue asociado a reservaciones y no se reutiliza como pedido de entradas.

El frontend necesitará pantallas de catálogo, ficha de evento, selección de entradas, confirmación/comprobante, «Mis entradas», administración de tipos/ventas y lector de acceso. Los contratos HTTP exactos se documentarán en `docs/frontend` al implementar la API.

## Fuera de esta primera versión

Pasarelas de pago, tarjetas, dinero real, reembolsos reales, compra sin cuenta, transferencia o reventa de entradas, asientos numerados, cupones, impuestos, billeteras externas como Google Wallet y entrada sin conexión. Son extensiones posibles; ninguna es necesaria para demostrar el flujo completo de compra simulada y acceso.

## Valores iniciales de configuración

| Opción | Valor inicial | Alcance |
| --- | --- | --- |
| `allowSalesWhileRunning` | `false` | Evento público. |
| `maxTicketsPerAccount` | `4` | Evento público; se aplica entre pedidos. |
| `admissionCapacity` | Sin límite duro | Evento público; independiente de `estimatedCapacity`. |
| `reservedInvitationCapacity` | `0` | Evento público; nunca reduce plazas ya comprometidas. |
| `allowRevokeAfterCheckIn` | `false` | Evento público; solo organizador puede revocar. |

Estas opciones se editan por el organizador con permisos del evento. Toda modificación se valida frente a pedidos, pases e invitaciones existentes y queda auditada.
