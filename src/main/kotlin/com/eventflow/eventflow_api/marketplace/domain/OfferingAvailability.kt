package com.eventflow.eventflow_api.marketplace.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "offering_availability")
class OfferingAvailability(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "availability_id") var id: UUID? = null,
    @Column(name = "offering_id", nullable = false) var offeringId: UUID,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "ends_at", nullable = false) var endsAt: Instant,
    @Column(nullable = false) var available: Boolean = true
)
