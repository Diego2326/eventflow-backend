package com.eventflow.eventflow_api.admin.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "audit_log")
class AuditLog(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "audit_log_id") var id: UUID? = null,
    @Column(name = "actor_user_id") var actorUserId: UUID? = null,
    @Column(name = "event_id") var eventId: UUID? = null,
    @Column(nullable = false) var action: String,
    @Column(name = "target_type") var targetType: String? = null,
    @Column(name = "target_id") var targetId: UUID? = null,
    @Column(nullable = false, columnDefinition = "text") var details: String = "{}",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
