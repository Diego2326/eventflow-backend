package com.eventflow.eventflow_api.event.application.port

import com.eventflow.eventflow_api.event.domain.EventCollaborator
import com.eventflow.eventflow_api.event.domain.EventCollaboratorId
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface EventCollaboratorRepositoryPort : CrudPort<EventCollaborator, EventCollaboratorId> {
    fun existsByEventIdAndUserId(eventId: UUID, userId: UUID): Boolean
    fun findByEventIdAndUserId(eventId: UUID, userId: UUID): EventCollaborator?
    fun findAllByEventId(eventId: UUID): List<EventCollaborator>
}
