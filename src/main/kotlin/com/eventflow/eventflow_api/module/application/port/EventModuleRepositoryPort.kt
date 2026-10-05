package com.eventflow.eventflow_api.module.application.port

import com.eventflow.eventflow_api.module.domain.EventModule
import com.eventflow.eventflow_api.module.domain.EventModuleId
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface EventModuleRepositoryPort : CrudPort<EventModule, EventModuleId> {
    fun findAllByEventIdOrderByDisplayOrder(eventId: UUID): List<EventModule>
}
