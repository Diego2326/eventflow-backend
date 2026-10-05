package com.eventflow.eventflow_api.marketplace.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "review")
class Review(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "review_id") var id: UUID? = null,
    @Column(name = "reservation_id", nullable = false) var reservationId: UUID,
    @Column(name = "author_user_id", nullable = false) var authorUserId: UUID,
    @Column(nullable = false) var rating: Int,
    @Column(columnDefinition = "text") var comment: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
