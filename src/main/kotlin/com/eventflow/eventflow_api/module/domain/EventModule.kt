package com.eventflow.eventflow_api.module.domain

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "event_module") @IdClass(EventModuleId::class)
class EventModule(
    @Id @Column(name = "event_id") var eventId: UUID = UUID.randomUUID(),
    @Id @Column(name = "module_code") var moduleCode: String = "",
    @Column(nullable = false) var enabled: Boolean = true,
    @Column(name = "display_order", nullable = false) var displayOrder: Int = 0,
    @Column(nullable = false) var featured: Boolean = false,
    @Column(nullable = false, columnDefinition = "text") var configuration: String = "{}",
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)
data class EventModuleId(var eventId: UUID? = null, var moduleCode: String? = null) : Serializable
