package com.eventflow.eventflow_api.event.infrastructure.persistence

import com.eventflow.eventflow_api.event.application.port.EventCollaboratorRepositoryPort
import com.eventflow.eventflow_api.event.domain.EventCollaborator
import com.eventflow.eventflow_api.event.domain.EventCollaboratorId

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EventCollaboratorRepository : JpaRepository<EventCollaborator, EventCollaboratorId>, EventCollaboratorRepositoryPort {
    override fun existsByEventIdAndUserId(eventId: UUID, userId: UUID): Boolean
    override fun findByEventIdAndUserId(eventId: UUID, userId: UUID): EventCollaborator?
    override fun findAllByEventId(eventId: UUID): List<EventCollaborator>
}
