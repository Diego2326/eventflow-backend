package com.eventflow.eventflow_api.agenda.application.port

import com.eventflow.eventflow_api.agenda.domain.AgendaItem
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.time.Instant
import java.util.UUID

interface AgendaItemRepositoryPort : CrudPort<AgendaItem, UUID> {
    fun findLocked(id: UUID): AgendaItem?
    fun findAllByEventIdOrderByStartsAt(eventId: UUID): List<AgendaItem>
    fun findAllByStartsAtBetweenAndStatusNot(from: Instant, to: Instant, status: String): List<AgendaItem>
}
