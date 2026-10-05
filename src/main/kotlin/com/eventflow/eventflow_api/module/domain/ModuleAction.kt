package com.eventflow.eventflow_api.module.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "module_action")
class ModuleAction(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "module_action_id") var id: UUID? = null,
    @Column(name = "module_record_id", nullable = false) var moduleRecordId: UUID,
    @Column(name = "actor_user_id") var actorUserId: UUID? = null,
    @Column(name = "invitation_id") var invitationId: UUID? = null,
    @Column(name = "action_type", nullable = false) var actionType: String,
    @Column(nullable = false) var quantity: Int = 1,
    @Column(name = "unique_action", nullable = false) var uniqueAction: Boolean = true,
    @Column(nullable = false, columnDefinition = "text") var payload: String = "{}",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
