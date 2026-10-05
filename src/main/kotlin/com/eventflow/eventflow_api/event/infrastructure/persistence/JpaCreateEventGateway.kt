package com.eventflow.eventflow_api.event.infrastructure.persistence

import com.eventflow.eventflow_api.event.application.CreateEventGateway
import com.eventflow.eventflow_api.event.application.NewEvent
import com.eventflow.eventflow_api.event.domain.EventEntity
import com.eventflow.eventflow_api.module.domain.EventModule
import com.eventflow.eventflow_api.module.infrastructure.persistence.EventModuleRepository
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository

import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaCreateEventGateway(
    private val events: EventRepository,
    private val catalog: ModuleCatalogRepository,
    private val modules: EventModuleRepository
) : CreateEventGateway {
    override fun save(event: NewEvent, suggestedModules: List<String>): UUID {
        val saved = events.save(EventEntity(
            ownerUserId = event.ownerUserId, name = event.name, type = event.type,
            description = event.description, startsAt = event.startsAt, endsAt = event.endsAt,
            timezone = event.timezone, location = event.location,
            estimatedCapacity = event.estimatedCapacity, budget = event.budget,
            reentryAllowed = event.reentryAllowed
        ))
        val id = requireNotNull(saved.id)
        suggestedModules.forEachIndexed { index, code ->
            if (catalog.findById(code).map { it.globallyEnabled }.orElse(false))
                modules.save(EventModule(id, code, displayOrder = index))
        }
        return id
    }
}
