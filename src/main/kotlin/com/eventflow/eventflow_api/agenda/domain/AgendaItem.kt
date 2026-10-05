package com.eventflow.eventflow_api.agenda.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "agenda_item")
class AgendaItem(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "agenda_item_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(nullable = false) var title: String,
    @Column(columnDefinition = "text") var description: String? = null,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "ends_at", nullable = false) var endsAt: Instant,
    var zone: String? = null,
    var responsible: String? = null,
    @Column(nullable = false) var status: String = "SCHEDULED",
    var capacity: Int? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
