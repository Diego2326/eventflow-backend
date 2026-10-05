package com.eventflow.eventflow_api.agenda.domain

import jakarta.persistence.*
import java.io.Serializable
import java.util.UUID

@Entity @Table(name = "agenda_favorite") @IdClass(AgendaFavoriteId::class)
class AgendaFavorite(
    @Id @Column(name = "agenda_item_id") var agendaItemId: UUID = UUID.randomUUID(),
    @Id @Column(name = "user_id") var userId: UUID = UUID.randomUUID()
)
data class AgendaFavoriteId(var agendaItemId: UUID? = null, var userId: UUID? = null) : Serializable
