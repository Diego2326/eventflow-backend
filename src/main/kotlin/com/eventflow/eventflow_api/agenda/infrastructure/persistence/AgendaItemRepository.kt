package com.eventflow.eventflow_api.agenda.infrastructure.persistence

import com.eventflow.eventflow_api.agenda.application.port.AgendaItemRepositoryPort
import com.eventflow.eventflow_api.agenda.domain.AgendaItem

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface AgendaItemRepository : JpaRepository<AgendaItem, UUID>, AgendaItemRepositoryPort {
    override fun findAllByEventIdOrderByStartsAt(eventId: UUID): List<AgendaItem>
    override fun findAllByStartsAtBetweenAndStatusNot(from: Instant, to: Instant, status: String): List<AgendaItem>
}
