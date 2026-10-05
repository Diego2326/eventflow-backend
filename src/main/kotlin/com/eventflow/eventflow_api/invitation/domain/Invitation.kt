package com.eventflow.eventflow_api.invitation.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "invitation")
class Invitation(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "invitation_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "linked_user_id") var linkedUserId: UUID? = null,
    @Column(name = "guest_name", nullable = false) var guestName: String,
    @Column(name = "guest_email") var guestEmail: String? = null,
    @Column(name = "token_hash", nullable = false, unique = true) var tokenHash: String,
    @Column(name = "token_expires_at") var tokenExpiresAt: Instant? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: InvitationStatus = InvitationStatus.PENDING,
    @Column(name = "allowed_capacity", nullable = false) var allowedCapacity: Int = 1,
    @Column(nullable = false, columnDefinition = "text") var companions: String = "[]",
    @Column(name = "table_label") var tableLabel: String? = null,
    @Column(name = "seat_label") var seatLabel: String? = null,
    @Column(name = "sector_label") var sectorLabel: String? = null,
    @Column(name = "revoked_at") var revokedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
