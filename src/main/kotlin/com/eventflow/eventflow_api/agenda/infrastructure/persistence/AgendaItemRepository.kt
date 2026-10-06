package com.eventflow.eventflow_api.agenda.infrastructure.persistence

import com.eventflow.eventflow_api.agenda.application.port.AgendaItemRepositoryPort
import com.eventflow.eventflow_api.agenda.domain.AgendaItem

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID

interface AgendaItemRepository : JpaRepository<AgendaItem, UUID>, AgendaItemRepositoryPort {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AgendaItem a where a.id = :id")
    override fun findLocked(@Param("id") id: UUID): AgendaItem?
    override fun findAllByEventIdOrderByStartsAt(eventId: UUID): List<AgendaItem>
    override fun findAllByStartsAtBetweenAndStatusNot(from: Instant, to: Instant, status: String): List<AgendaItem>
}
