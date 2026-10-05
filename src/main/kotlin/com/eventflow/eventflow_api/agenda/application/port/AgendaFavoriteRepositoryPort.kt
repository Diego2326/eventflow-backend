package com.eventflow.eventflow_api.agenda.application.port

import com.eventflow.eventflow_api.agenda.domain.AgendaFavorite
import com.eventflow.eventflow_api.agenda.domain.AgendaFavoriteId
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface AgendaFavoriteRepositoryPort : CrudPort<AgendaFavorite, AgendaFavoriteId> {
    fun findAllByUserId(userId: UUID): List<AgendaFavorite>
    fun findAllByAgendaItemIdIn(agendaItemIds: Collection<UUID>): List<AgendaFavorite>
}
