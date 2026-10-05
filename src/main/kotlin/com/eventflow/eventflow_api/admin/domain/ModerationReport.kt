package com.eventflow.eventflow_api.admin.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "moderation_report")
class ModerationReport(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "report_id") var id: UUID? = null,
    @Column(name = "reporter_user_id") var reporterUserId: UUID? = null,
    @Column(name = "event_id") var eventId: UUID? = null,
    @Column(name = "target_type", nullable = false) var targetType: String,
    @Column(name = "target_id", nullable = false) var targetId: UUID,
    @Column(nullable = false, columnDefinition = "text") var reason: String,
    @Column(nullable = false) var status: String = "PENDING",
    @Column(columnDefinition = "text") var resolution: String? = null,
    @Column(name = "resolved_by") var resolvedBy: UUID? = null,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
