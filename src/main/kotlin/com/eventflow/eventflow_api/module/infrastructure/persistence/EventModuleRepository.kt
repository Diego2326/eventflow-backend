package com.eventflow.eventflow_api.module.infrastructure.persistence

import com.eventflow.eventflow_api.module.application.port.EventModuleRepositoryPort
import com.eventflow.eventflow_api.module.domain.EventModule
import com.eventflow.eventflow_api.module.domain.EventModuleId

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EventModuleRepository : JpaRepository<EventModule, EventModuleId>, EventModuleRepositoryPort { override fun findAllByEventIdOrderByDisplayOrder(eventId: UUID): List<EventModule> }
