package com.eventflow.eventflow_api.event.application.port

import com.eventflow.eventflow_api.event.domain.EventEntity
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface EventRepositoryPort : CrudPort<EventEntity, UUID> {
    fun findAccessible(userId: UUID): List<EventEntity>
}
