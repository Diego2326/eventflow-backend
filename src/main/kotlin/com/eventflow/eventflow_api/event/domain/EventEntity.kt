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
    @Column(name = "reentry_allowed", nullable = false) var reentryAllowed: Boolean = false,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)
