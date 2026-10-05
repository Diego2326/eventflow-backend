package com.eventflow.eventflow_api.marketplace.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "simulated_payment")
class SimulatedPayment(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "payment_id") var id: UUID? = null,
    @Column(name = "reservation_id", nullable = false) var reservationId: UUID,
    @Column(nullable = false) var amount: BigDecimal,
    @Column(nullable = false) var status: String,
    @Column(name = "paid_at", nullable = false) var paidAt: Instant = Instant.now(),
    @Column(nullable = false, unique = true) var reference: String
)
