package com.eventflow.eventflow_api.event.application

import com.eventflow.eventflow_api.event.domain.EventLifecycle
import com.eventflow.eventflow_api.event.domain.EventStatus

import java.util.UUID

/** Puerto de salida del caso de uso; no expone entidades de persistencia. */
interface EventStatusGateway {
    fun hasEnabledModule(eventId: UUID): Boolean
    fun changeStatus(eventId: UUID, status: EventStatus)
}

sealed class EventTransitionFailure(message: String) : RuntimeException(message) {
    class Invalid(from: EventStatus, to: EventStatus) :
        EventTransitionFailure("Transición $from -> $to no permitida")
    class NoEnabledModules : EventTransitionFailure("Debes habilitar al menos un módulo")
}

class TransitionEvent(private val gateway: EventStatusGateway) {
    fun execute(eventId: UUID, current: EventStatus, target: EventStatus): EventStatus {
        if (!EventLifecycle.canTransition(current, target))
            throw EventTransitionFailure.Invalid(current, target)
        if (target == EventStatus.PUBLISHED && !gateway.hasEnabledModule(eventId))
            throw EventTransitionFailure.NoEnabledModules()
        gateway.changeStatus(eventId, target)
        return target
    }
}
