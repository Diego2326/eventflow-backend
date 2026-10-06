package com.eventflow.eventflow_api.ticketing.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.application.port.EventRepositoryPort
import com.eventflow.eventflow_api.event.domain.EventEntity
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.event.domain.EventVisibility
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.shared.application.error.*
import com.eventflow.eventflow_api.ticketing.application.port.*
import com.eventflow.eventflow_api.ticketing.domain.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class TicketSettingsRequest(val visibility: EventVisibility, val allowSalesWhileRunning: Boolean = false,
    val maxTicketsPerAccount: Int = 4, val admissionCapacity: Int? = null,
    val reservedInvitationCapacity: Int = 0, val allowRevokeAfterCheckIn: Boolean = false)
data class TicketSettingsResponse(val visibility: EventVisibility, val allowSalesWhileRunning: Boolean,
    val maxTicketsPerAccount: Int, val admissionCapacity: Int?, val reservedInvitationCapacity: Int,
    val allowRevokeAfterCheckIn: Boolean)
data class TicketTypeRequest(val name: String, val description: String? = null, val price: BigDecimal,
    val currency: String = "GTQ", val capacity: Int, val maxPerOrder: Int = 4,
    val salesStart: Instant, val salesEnd: Instant, val enabled: Boolean = true)
data class TicketTypeResponse(val id: UUID, val name: String, val description: String?, val price: BigDecimal,
    val currency: String, val capacity: Int, val available: Int, val maxPerOrder: Int,
    val salesStart: Instant, val salesEnd: Instant, val enabled: Boolean)
data class PublicEventResponse(val id: UUID, val name: String, val type: String, val description: String?,
    val startsAt: Instant, val endsAt: Instant?, val timezone: String, val location: String?,
    val status: EventStatus, val ticketTypes: List<TicketTypeResponse>)
data class TicketLineRequest(val ticketTypeId: UUID, val quantity: Int)
data class BuyTicketsRequest(val lines: List<TicketLineRequest>)
data class TicketPassResponse(val id: UUID, val eventId: UUID, val orderId: UUID, val typeName: String,
    val unitPrice: BigDecimal, val ordinal: Int, val status: String, val checkedIn: Boolean,
    val qrPayload: String?, val cancellationReason: String?)
data class TicketOrderResponse(val id: UUID, val eventId: UUID, val buyerUserId: UUID, val status: String, val currency: String,
    val total: BigDecimal, val reference: String, val createdAt: Instant, val simulated: Boolean = true,
    val message: String = "Compra simulada — no se realizó ningún cobro",
    val tickets: List<TicketPassResponse>)
data class TicketCheckResponse(val ticket: TicketPassResponse, val action: String, val checkedAt: Instant)
data class TicketSalesResponse(val orders: Int, val activeTickets: Int, val checkedIn: Int,
    val simulatedGross: BigDecimal, val simulatedReversed: BigDecimal)
data class TicketRevokeRequest(val reason: String)

@Service
class TicketingService(private val events: EventRepositoryPort, private val eventService: EventService,
    private val invitations: InvitationRepositoryPort, private val types: TicketTypePort,
    private val orders: TicketOrderPort, private val tickets: EventTicketPort,
    private val logs: TicketAccessLogPort, private val settingsAudit: TicketSettingsAuditPort,
    private val publicCatalog: PublicEventCatalogPort) {
    private val random = SecureRandom()

    @Transactional(readOnly = true)
    fun catalog(q: String?, type: String?, from: Instant?, page: Int, size: Int): List<PublicEventResponse> {
        if (page < 0 || size !in 1..100) throw BadRequestException("Paginación inválida")
        if (page > Int.MAX_VALUE / size) throw BadRequestException("Paginación inválida")
        return publicCatalog.search(q, type, from, page * size, size).map(::publicResponse)
    }

    @Transactional(readOnly = true)
    fun publicEvent(eventId: UUID): PublicEventResponse {
        val event = event(eventId)
        if (event.visibility != EventVisibility.PUBLIC || event.status !in setOf(EventStatus.PUBLISHED, EventStatus.RUNNING))
            throw NotFoundException("Evento público no encontrado")
        return publicResponse(event)
    }

    @Transactional
    fun settings(userId: UUID, eventId: UUID, r: TicketSettingsRequest): TicketSettingsResponse {
        eventService.owned(userId, eventId)
        val e = locked(eventId)
        val before = settingsResponse(e).toString()
        if (r.maxTicketsPerAccount < 1 || r.reservedInvitationCapacity < 0 || r.admissionCapacity?.let { it < 1 } == true)
            throw BadRequestException("Configuración de entradas inválida")
        val committed = committedTickets(eventId)
        val invitationsCommitted = invitationCapacity(eventId)
        if (r.admissionCapacity != null && committed + maxOf(r.reservedInvitationCapacity, invitationsCommitted) > r.admissionCapacity)
            throw ConflictException("El aforo es menor que las plazas ya comprometidas")
        if (tickets.findAllByEventId(eventId).count { it.status == "ACTIVE" } > 0 && r.visibility == EventVisibility.PRIVATE)
            throw ConflictException("No se puede ocultar un evento con entradas activas")
        val countsByBuyer = orders.findAllByEventIdOrderByCreatedAtDesc(eventId).groupBy { it.buyerUserId }
        if (countsByBuyer.any { (_, buyerOrders) -> buyerOrders.sumOf { order -> tickets.findAllByOrderId(requireNotNull(order.id)).count { it.status == "ACTIVE" } } > r.maxTicketsPerAccount })
            throw ConflictException("El máximo por cuenta es menor que las entradas ya adquiridas")
        e.visibility = r.visibility; e.allowSalesWhileRunning = r.allowSalesWhileRunning
        e.maxTicketsPerAccount = r.maxTicketsPerAccount; e.admissionCapacity = r.admissionCapacity
        e.reservedInvitationCapacity = r.reservedInvitationCapacity; e.allowRevokeAfterCheckIn = r.allowRevokeAfterCheckIn
        e.updatedAt = Instant.now()
        settingsAudit.save(TicketSettingsAudit(eventId = eventId, actorUserId = userId,
            beforeSnapshot = before, afterSnapshot = settingsResponse(e).toString()))
        return settingsResponse(e)
    }

    @Transactional(readOnly = true) fun settings(userId: UUID, eventId: UUID): TicketSettingsResponse = settingsResponse(eventService.owned(userId, eventId))

    @Transactional
    fun createType(userId: UUID, eventId: UUID, r: TicketTypeRequest): TicketTypeResponse {
        eventService.owned(userId, eventId)
        val e = locked(eventId)
        validateType(e, r)
        val t = types.save(TicketType(eventId = eventId, name = r.name.trim(), description = r.description?.trim(),
            price = r.price, currency = r.currency.uppercase(), capacity = r.capacity, maxPerOrder = r.maxPerOrder,
            salesStart = r.salesStart, salesEnd = r.salesEnd, enabled = r.enabled))
        return typeResponse(t)
    }

    @Transactional
    fun updateType(userId: UUID, eventId: UUID, typeId: UUID, r: TicketTypeRequest): TicketTypeResponse {
        eventService.owned(userId, eventId)
        val e = locked(eventId)
        val t = ticketType(eventId, typeId)
        validateType(e, r)
        if (r.capacity < tickets.findAllByTicketTypeId(typeId).count { it.status != "CANCELLED" })
            throw ConflictException("El cupo es menor que las entradas emitidas")
        t.name = r.name.trim(); t.description = r.description?.trim(); t.price = r.price
        t.currency = r.currency.uppercase(); t.capacity = r.capacity; t.maxPerOrder = r.maxPerOrder
        t.salesStart = r.salesStart; t.salesEnd = r.salesEnd; t.enabled = r.enabled
        return typeResponse(t)
    }

    @Transactional(readOnly = true) fun listTypes(userId: UUID, eventId: UUID): List<TicketTypeResponse> {
        eventService.owned(userId, eventId)
        return types.findAllByEventId(eventId).map(::typeResponse)
    }

    @Transactional
    fun buy(userId: UUID, eventId: UUID, key: String, r: BuyTicketsRequest): TicketOrderResponse {
        if (key.length !in 8..120 || r.lines.isEmpty() || r.lines.size > 20 || r.lines.any { it.quantity < 1 || it.quantity > 100 } ||
            r.lines.map { it.ticketTypeId }.distinct().size != r.lines.size) throw BadRequestException("Compra inválida")
        val fingerprint = sha256(eventId.toString() + ":" + r.lines.sortedBy { it.ticketTypeId }.joinToString(";") { "${it.ticketTypeId}:${it.quantity}" })
        val e = locked(eventId)
        orders.findByBuyerUserIdAndIdempotencyKey(userId, key)?.let {
            if (it.requestFingerprint != fingerprint) throw ConflictException("La clave de compra ya se usó con otro contenido")
            return orderResponse(it)
        }
        val now = Instant.now()
        if (e.visibility != EventVisibility.PUBLIC || e.status !in setOf(EventStatus.PUBLISHED, EventStatus.RUNNING) ||
            (e.status == EventStatus.RUNNING && !e.allowSalesWhileRunning)) throw ConflictException("La venta no está disponible")
        val selected = r.lines.map { line -> ticketType(eventId, line.ticketTypeId) to line.quantity }
        val currencies = selected.map { it.first.currency }.distinct()
        if (currencies.size != 1) throw BadRequestException("No se pueden mezclar monedas")
        val quantity = r.lines.sumOf { it.quantity }
        val buyerCount = orders.findAllByBuyerUserIdOrderByCreatedAtDesc(userId).filter { it.eventId == eventId }
            .sumOf { order -> tickets.findAllByOrderId(requireNotNull(order.id)).count { it.status == "ACTIVE" } }
        if (buyerCount + quantity > e.maxTicketsPerAccount) throw ConflictException("Supera el máximo de entradas por cuenta")
        if (e.admissionCapacity != null && committedTickets(eventId) + maxOf(e.reservedInvitationCapacity, invitationCapacity(eventId)) + quantity > e.admissionCapacity!!)
            throw ConflictException("Aforo agotado")
        selected.forEach { (t, amount) ->
            if (!t.enabled || now.isBefore(t.salesStart) || !now.isBefore(t.salesEnd) || amount > t.maxPerOrder)
                throw ConflictException("El tipo de entrada no está disponible")
            if (tickets.findAllByTicketTypeId(requireNotNull(t.id)).count { it.status != "CANCELLED" } + amount > t.capacity)
                throw ConflictException("Entradas agotadas: ${t.name}")
        }
        val total = selected.fold(BigDecimal.ZERO) { acc, (t, amount) -> acc + t.price.multiply(BigDecimal(amount)) }
        val order = orders.save(TicketOrder(eventId = eventId, buyerUserId = userId, idempotencyKey = key,
            requestFingerprint = fingerprint, currency = currencies.single(), total = total,
            reference = "EF-${UUID.randomUUID().toString().take(12).uppercase()}"))
        var ordinal = 0
        selected.forEach { (t, amount) -> repeat(amount) {
            ordinal++
            tickets.save(EventTicket(eventId = eventId, orderId = requireNotNull(order.id), ticketTypeId = requireNotNull(t.id),
                ordinal = ordinal, typeName = t.name, unitPrice = t.price, qrHash = sha256(randomToken())))
        } }
        return orderResponse(order)
    }

    @Transactional(readOnly = true) fun mine(userId: UUID): List<TicketOrderResponse> = orders.findAllByBuyerUserIdOrderByCreatedAtDesc(userId).map { orderResponse(it) }
    @Transactional(readOnly = true) fun myOrder(userId: UUID, id: UUID): TicketOrderResponse {
        val order = orders.findById(id).orElseThrow { NotFoundException("Compra no encontrada") }
        if (order.buyerUserId != userId) throw ForbiddenException("Compra ajena")
        return orderResponse(order)
    }
    @Transactional(readOnly = true) fun eventOrders(userId: UUID, eventId: UUID, page: Int, size: Int): List<TicketOrderResponse> {
        eventService.authorized(userId, eventId, "TICKETS")
        if (page < 0 || size !in 1..100) throw BadRequestException("Paginación inválida")
        return orders.findAllByEventIdOrderByCreatedAtDesc(eventId).drop(page * size).take(size).map { orderResponse(it, false) }
    }

    @Transactional
    fun cancelOrder(userId: UUID, orderId: UUID): TicketOrderResponse {
        val order = orders.findById(orderId).orElseThrow { NotFoundException("Compra no encontrada") }
        if (order.buyerUserId != userId) throw ForbiddenException("Compra ajena")
        locked(order.eventId)
        if (order.status == "CANCELLED") return orderResponse(order)
        if (!Instant.now().isBefore(event(order.eventId).startsAt)) throw ConflictException("Ya comenzó el evento")
        val passes = tickets.findAllByOrderId(orderId)
        if (passes.any { it.everCheckedIn }) throw ConflictException("Una entrada ya fue utilizada")
        cancelPasses(order, passes, "Cancelación del comprador", userId)
        return orderResponse(order)
    }

    @Transactional
    fun cancelEventOrder(userId: UUID, eventId: UUID, orderId: UUID, r: TicketRevokeRequest): TicketOrderResponse {
        eventService.authorized(userId, eventId, "TICKETS")
        if (r.reason.isBlank()) throw BadRequestException("Indica el motivo")
        locked(eventId)
        val order = orders.findById(orderId).orElseThrow { NotFoundException("Compra no encontrada") }
        if (order.eventId != eventId) throw NotFoundException("Compra no encontrada")
        if (order.status == "CANCELLED") return orderResponse(order, false)
        val passes = tickets.findAllByOrderId(orderId)
        if (passes.any { it.everCheckedIn }) throw ConflictException("Una entrada ya fue utilizada; revoca los pases individualmente")
        cancelPasses(order, passes, r.reason.trim(), userId)
        return orderResponse(order, false)
    }

    @Transactional
    fun revoke(userId: UUID, eventId: UUID, ticketId: UUID, r: TicketRevokeRequest): TicketPassResponse {
        eventService.authorized(userId, eventId, "TICKETS")
        val e = locked(eventId)
        if (r.reason.isBlank()) throw BadRequestException("Indica el motivo")
        val t = tickets.findLocked(ticketId) ?: throw NotFoundException("Entrada no encontrada")
        if (t.eventId != eventId) throw NotFoundException("Entrada no encontrada")
        if (t.status != "ACTIVE") return passResponse(t, false)
        if (t.everCheckedIn && !e.allowRevokeAfterCheckIn) throw ConflictException("No se permite revocar una entrada utilizada")
        t.status = if (t.everCheckedIn) "REVOKED" else "CANCELLED"
        t.cancelledAt = Instant.now(); t.cancellationReason = r.reason.trim(); t.cancelledBy = userId
        return passResponse(t, false)
    }

    @Transactional
    fun regenerate(userId: UUID, ticketId: UUID): TicketPassResponse {
        val t = tickets.findLocked(ticketId) ?: throw NotFoundException("Entrada no encontrada")
        val order = orders.findById(t.orderId).orElseThrow()
        if (order.buyerUserId != userId || t.status != "ACTIVE") throw ForbiddenException("Entrada no disponible")
        t.qrHash = sha256(randomToken())
        return passResponse(t)
    }

    @Transactional
    fun checkQr(userId: UUID, eventId: UUID, qrPayload: String, incoming: Boolean): TicketCheckResponse {
        eventService.authorized(userId, eventId, "CHECK_IN")
        val hash = qrPayload.removePrefix("eventflow:ticket:")
        if (!qrPayload.startsWith("eventflow:ticket:") || !hash.matches(Regex("[0-9a-f]{64}"))) throw BadRequestException("QR inválido")
        val found = tickets.findByQrHash(hash) ?: throw NotFoundException("Entrada no encontrada")
        val t = tickets.findLocked(requireNotNull(found.id)) ?: throw NotFoundException("Entrada no encontrada")
        if (t.eventId != eventId) throw NotFoundException("Entrada no encontrada")
        val e = event(eventId)
        if (e.status !in setOf(EventStatus.PUBLISHED, EventStatus.RUNNING)) throw ConflictException("El evento no admite accesos")
        if (incoming) {
            if (t.status != "ACTIVE") throw ConflictException("Entrada anulada")
            if (t.checkedIn) throw ConflictException("La entrada ya fue utilizada")
            if (t.everCheckedIn && !e.reentryAllowed) throw ConflictException("El reingreso no está permitido")
            t.checkedIn = true; t.everCheckedIn = true
        } else {
            if (!t.checkedIn) throw ConflictException("La persona no está dentro")
            t.checkedIn = false
        }
        val log = logs.save(TicketAccessLog(ticketId = requireNotNull(t.id), action = if (incoming) "CHECK_IN" else "CHECK_OUT", performedBy = userId))
        return TicketCheckResponse(passResponse(t), log.action, log.createdAt)
    }

    @Transactional(readOnly = true) fun sales(userId: UUID, eventId: UUID): TicketSalesResponse {
        eventService.authorized(userId, eventId, "TICKETS")
        val os = orders.findAllByEventIdOrderByCreatedAtDesc(eventId)
        val ts = tickets.findAllByEventId(eventId)
        return TicketSalesResponse(os.size, ts.count { it.status == "ACTIVE" }, ts.count { it.checkedIn },
            os.fold(BigDecimal.ZERO) { a, o -> a + o.total },
            ts.filter { it.status == "CANCELLED" }.fold(BigDecimal.ZERO) { a, t -> a + t.unitPrice })
    }

    fun committedTickets(eventId: UUID): Int = tickets.findAllByEventId(eventId).count { it.status != "CANCELLED" }
    fun invitationCapacity(eventId: UUID): Int = invitations.findAllByEventId(eventId).filter { it.revokedAt == null }.sumOf { it.allowedCapacity }
    fun ensureInvitationCapacity(eventId: UUID, additional: Int) {
        val e = locked(eventId)
        if (e.admissionCapacity != null && committedTickets(eventId) + maxOf(e.reservedInvitationCapacity, invitationCapacity(eventId) + additional) > e.admissionCapacity!!)
            throw ConflictException("El aforo no permite agregar esta invitación")
    }
    private fun locked(id: UUID) = events.findLocked(id) ?: throw NotFoundException("Evento no encontrado")
    private fun event(id: UUID) = events.findById(id).orElseThrow { NotFoundException("Evento no encontrado") }
    private fun ticketType(eventId: UUID, typeId: UUID): TicketType = types.findById(typeId).orElseThrow { NotFoundException("Tipo de entrada no encontrado") }.also {
        if (it.eventId != eventId) throw NotFoundException("Tipo de entrada no encontrado")
    }
    private fun validateType(e: EventEntity, r: TicketTypeRequest) {
        if (r.name.isBlank() || r.name.length > 120 || r.price < BigDecimal.ZERO || r.price.scale() > 2 ||
            !r.currency.matches(Regex("[A-Z]{3}", RegexOption.IGNORE_CASE)) || r.capacity < 1 || r.maxPerOrder !in 1..100 ||
            !r.salesEnd.isAfter(r.salesStart) || r.salesEnd.isAfter(e.endsAt ?: e.startsAt.plusSeconds(86400)))
            throw BadRequestException("Tipo de entrada inválido")
    }
    private fun publicResponse(e: EventEntity) = PublicEventResponse(requireNotNull(e.id), e.name, e.type, e.description,
        e.startsAt, e.endsAt, e.timezone, e.location, e.status, types.findAllByEventId(requireNotNull(e.id)).filter { it.enabled }.map(::typeResponse))
    private fun typeResponse(t: TicketType): TicketTypeResponse {
        val typeAvailable = (t.capacity - tickets.findAllByTicketTypeId(requireNotNull(t.id)).count { it.status != "CANCELLED" }).coerceAtLeast(0)
        val e = event(t.eventId)
        val globalAvailable = e.admissionCapacity?.let { (it - committedTickets(t.eventId) - maxOf(e.reservedInvitationCapacity, invitationCapacity(t.eventId))).coerceAtLeast(0) }
        return TicketTypeResponse(requireNotNull(t.id), t.name, t.description, t.price, t.currency,
            t.capacity, if (globalAvailable == null) typeAvailable else minOf(typeAvailable, globalAvailable),
            t.maxPerOrder, t.salesStart, t.salesEnd, t.enabled)
    }
    private fun settingsResponse(e: EventEntity) = TicketSettingsResponse(e.visibility, e.allowSalesWhileRunning,
        e.maxTicketsPerAccount, e.admissionCapacity, e.reservedInvitationCapacity, e.allowRevokeAfterCheckIn)
    private fun cancelPasses(order: TicketOrder, passes: List<EventTicket>, reason: String, actor: UUID) {
        val now = Instant.now()
        passes.forEach { if (it.status == "ACTIVE") { it.status = "CANCELLED"; it.cancelledAt = now; it.cancellationReason = reason; it.cancelledBy = actor } }
        order.status = "CANCELLED"; order.cancelledAt = now
    }
    private fun passResponse(t: EventTicket, includeQr: Boolean = true) = TicketPassResponse(requireNotNull(t.id), t.eventId, t.orderId, t.typeName,
        t.unitPrice, t.ordinal, t.status, t.checkedIn, if (includeQr) "eventflow:ticket:${t.qrHash}" else null, t.cancellationReason)
    private fun orderResponse(o: TicketOrder, includeQr: Boolean = true) = TicketOrderResponse(requireNotNull(o.id), o.eventId, o.buyerUserId, o.status, o.currency,
        o.total, o.reference, o.createdAt, tickets = tickets.findAllByOrderId(requireNotNull(o.id)).sortedBy { it.ordinal }.map { passResponse(it, includeQr) })
    private fun randomToken() = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
