package com.eventflow.eventflow_api.event.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "event")
class EventEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "event_id") var id: UUID? = null,
    @Column(name = "owner_user_id", nullable = false) var ownerUserId: UUID,
    @Column(name = "event_name", nullable = false) var name: String,
    @Column(name = "event_type", nullable = false) var type: String,
    @Column(columnDefinition = "text") var description: String? = null,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "ends_at") var endsAt: Instant? = null,
    @Column(nullable = false) var timezone: String = "America/Guatemala",
    var location: String? = null,
    @Column(name = "estimated_capacity") var estimatedCapacity: Int? = null,
    var budget: BigDecimal? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: EventStatus = EventStatus.DRAFT,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var visibility: EventVisibility = EventVisibility.PRIVATE,
    @Column(name = "allow_sales_while_running", nullable = false) var allowSalesWhileRunning: Boolean = false,
    @Column(name = "max_tickets_per_account", nullable = false) var maxTicketsPerAccount: Int = 4,
    @Column(name = "admission_capacity") var admissionCapacity: Int? = null,
    @Column(name = "reserved_invitation_capacity", nullable = false) var reservedInvitationCapacity: Int = 0,
    @Column(name = "allow_revoke_after_check_in", nullable = false) var allowRevokeAfterCheckIn: Boolean = false,
    @Column(name = "reentry_allowed", nullable = false) var reentryAllowed: Boolean = false,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)
