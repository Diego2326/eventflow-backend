package com.eventflow.eventflow_api.assistance.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "assistance_request")
class AssistanceRequest(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "assistance_request_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "invitation_id") var invitationId: UUID? = null,
    @Column(name = "requester_user_id") var requesterUserId: UUID? = null,
    @Column(name = "assigned_user_id") var assignedUserId: UUID? = null,
    @Column(nullable = false) var category: String,
    @Column(columnDefinition = "text") var details: String? = null,
    var location: String? = null,
    @Column(nullable = false) var priority: Int = 0,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: AssistanceStatus = AssistanceStatus.RECEIVED,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)
