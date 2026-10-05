package com.eventflow.eventflow_api.marketplace.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "reservation")
class Reservation(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "reservation_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "offering_id", nullable = false) var offeringId: UUID,
    @Column(name = "requester_user_id", nullable = false) var requesterUserId: UUID,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "ends_at", nullable = false) var endsAt: Instant,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: ReservationStatus = ReservationStatus.PENDING,
    @Column(columnDefinition = "text") var note: String? = null,
    @Column(name = "decided_at") var decidedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
