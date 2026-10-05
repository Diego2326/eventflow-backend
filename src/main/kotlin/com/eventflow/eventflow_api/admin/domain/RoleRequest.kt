package com.eventflow.eventflow_api.admin.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "role_request")
class RoleRequest(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "role_request_id") var id: UUID? = null,
    @Column(name = "user_id", nullable = false) var userId: UUID,
    @Column(name = "requested_role", nullable = false) var requestedRole: String,
    @Column(nullable = false) var status: String = "PENDING",
    @Column(name = "decided_by") var decidedBy: UUID? = null,
    @Column(name = "decided_at") var decidedAt: Instant? = null,
    @Column(columnDefinition = "text") var reason: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
