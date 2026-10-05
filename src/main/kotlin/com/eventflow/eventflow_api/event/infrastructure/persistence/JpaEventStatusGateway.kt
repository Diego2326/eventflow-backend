package com.eventflow.eventflow_api.event.infrastructure.persistence

import com.eventflow.eventflow_api.event.application.EventStatusGateway
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.module.infrastructure.persistence.EventModuleRepository

import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JpaEventStatusGateway(
    private val events: EventRepository,
    private val modules: EventModuleRepository
) : EventStatusGateway {
    override fun hasEnabledModule(eventId: UUID): Boolean =
        modules.findAllByEventIdOrderByDisplayOrder(eventId).any { it.enabled }

    override fun changeStatus(eventId: UUID, status: EventStatus) {
        val event = events.findById(eventId).orElseThrow()
        event.status = status
        event.updatedAt = Instant.now()
        events.save(event)
    }
}
