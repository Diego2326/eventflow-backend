package com.eventflow.eventflow_api.infrastructure.persistence

import com.eventflow.eventflow_api.application.event.EventStatusGateway
import com.eventflow.eventflow_api.domain.EventStatus
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
