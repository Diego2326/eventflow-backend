package com.eventflow.eventflow_api.agenda.infrastructure.persistence

import com.eventflow.eventflow_api.agenda.application.port.AgendaFavoriteRepositoryPort
import com.eventflow.eventflow_api.agenda.domain.AgendaFavorite
import com.eventflow.eventflow_api.agenda.domain.AgendaFavoriteId

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AgendaFavoriteRepository : JpaRepository<AgendaFavorite, AgendaFavoriteId>, AgendaFavoriteRepositoryPort {
    override fun findAllByUserId(userId: UUID): List<AgendaFavorite>
    override fun findAllByAgendaItemIdIn(agendaItemIds: Collection<UUID>): List<AgendaFavorite>
}
