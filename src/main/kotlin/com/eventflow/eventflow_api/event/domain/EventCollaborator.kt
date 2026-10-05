package com.eventflow.eventflow_api.event.domain

import jakarta.persistence.*
import java.io.Serializable
import java.util.UUID

@Entity @Table(name = "event_collaborator") @IdClass(EventCollaboratorId::class)
class EventCollaborator(
    @Id @Column(name = "event_id") var eventId: UUID = UUID.randomUUID(),
    @Id @Column(name = "user_id") var userId: UUID = UUID.randomUUID(),
    @Column(nullable = false, columnDefinition = "text") var permissions: String = ""
)
data class EventCollaboratorId(var eventId: UUID? = null, var userId: UUID? = null) : Serializable
