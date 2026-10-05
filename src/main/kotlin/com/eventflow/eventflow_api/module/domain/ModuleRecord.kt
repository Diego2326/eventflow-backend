package com.eventflow.eventflow_api.module.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "module_record")
class ModuleRecord(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "module_record_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "module_code", nullable = false) var moduleCode: String,
    @Column(name = "record_type", nullable = false) var recordType: String,
    @Column(name = "owner_user_id") var ownerUserId: UUID? = null,
    @Column(name = "invitation_id") var invitationId: UUID? = null,
    @Column(name = "parent_record_id") var parentRecordId: UUID? = null,
    @Column(nullable = false) var status: String = "ACTIVE",
    var title: String? = null,
    @Column(nullable = false, columnDefinition = "text") var payload: String = "{}",
    var capacity: Int? = null,
    @Column(name = "current_count", nullable = false) var currentCount: Int = 0,
    @Column(name = "starts_at") var startsAt: Instant? = null,
    @Column(name = "ends_at") var endsAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Version var version: Long = 0
)
