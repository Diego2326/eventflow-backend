package com.eventflow.eventflow_api.ticketing.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "ticket_type")
class TicketType(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "ticket_type_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(nullable = false) var name: String,
    @Column(columnDefinition = "text") var description: String? = null,
    @Column(nullable = false) var price: BigDecimal,
    @Column(nullable = false) var currency: String,
    @Column(nullable = false) var capacity: Int,
    @Column(name = "max_per_order", nullable = false) var maxPerOrder: Int,
    @Column(name = "sales_start", nullable = false) var salesStart: Instant,
    @Column(name = "sales_end", nullable = false) var salesEnd: Instant,
    @Column(nullable = false) var enabled: Boolean = true,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "ticket_order")
class TicketOrder(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "ticket_order_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "buyer_user_id", nullable = false) var buyerUserId: UUID,
    @Column(name = "idempotency_key", nullable = false) var idempotencyKey: String,
    @Column(name = "request_fingerprint", nullable = false) var requestFingerprint: String,
    @Column(nullable = false) var status: String = "CONFIRMED",
    @Column(nullable = false) var currency: String,
    @Column(nullable = false) var total: BigDecimal,
    @Column(nullable = false) var reference: String,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "cancelled_at") var cancelledAt: Instant? = null
)

@Entity @Table(name = "event_ticket")
class EventTicket(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "ticket_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "order_id", nullable = false) var orderId: UUID,
    @Column(name = "ticket_type_id", nullable = false) var ticketTypeId: UUID,
    @Column(nullable = false) var ordinal: Int,
    @Column(name = "type_name", nullable = false) var typeName: String,
    @Column(name = "unit_price", nullable = false) var unitPrice: BigDecimal,
    @Column(nullable = false) var status: String = "ACTIVE",
    @Column(name = "qr_hash", nullable = false, unique = true) var qrHash: String,
    @Column(name = "checked_in", nullable = false) var checkedIn: Boolean = false,
    @Column(name = "ever_checked_in", nullable = false) var everCheckedIn: Boolean = false,
    @Column(name = "cancelled_at") var cancelledAt: Instant? = null,
    @Column(name = "cancellation_reason", columnDefinition = "text") var cancellationReason: String? = null,
    @Column(name = "cancelled_by") var cancelledBy: UUID? = null
)

@Entity @Table(name = "ticket_access_log")
class TicketAccessLog(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "ticket_access_log_id") var id: UUID? = null,
    @Column(name = "ticket_id", nullable = false) var ticketId: UUID,
    @Column(nullable = false) var action: String,
    @Column(name = "performed_by", nullable = false) var performedBy: UUID,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "ticket_settings_audit")
class TicketSettingsAudit(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "audit_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "actor_user_id", nullable = false) var actorUserId: UUID,
    @Column(name = "before_snapshot", nullable = false, columnDefinition = "text") var beforeSnapshot: String,
    @Column(name = "after_snapshot", nullable = false, columnDefinition = "text") var afterSnapshot: String,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
