package com.eventflow.eventflow_api.event.domain

/** Reglas de estado independientes de Spring y de la persistencia. */
object EventLifecycle {
    private val transitions = mapOf(
        EventStatus.DRAFT to setOf(EventStatus.PUBLISHED, EventStatus.CANCELLED),
        EventStatus.PUBLISHED to setOf(EventStatus.RUNNING, EventStatus.CANCELLED),
        EventStatus.RUNNING to setOf(EventStatus.FINISHED, EventStatus.CANCELLED),
        EventStatus.FINISHED to emptySet(),
        EventStatus.CANCELLED to emptySet()
    )

    fun canTransition(from: EventStatus, to: EventStatus): Boolean = to in transitions.getValue(from)
}
