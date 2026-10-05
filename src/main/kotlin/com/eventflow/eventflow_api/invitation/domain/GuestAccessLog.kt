package com.eventflow.eventflow_api.invitation.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "guest_access_log")
class GuestAccessLog(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "access_log_id") var id: UUID? = null,
    @Column(name = "invitation_id", nullable = false) var invitationId: UUID,
    @Column(nullable = false) var action: String,
    @Column(nullable = false) var quantity: Int = 1,
    @Column(name="member_index") var memberIndex:Int?=null,
    @Column(name = "performed_by") var performedBy: UUID? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
