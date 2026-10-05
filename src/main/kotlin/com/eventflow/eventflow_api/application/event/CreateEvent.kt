package com.eventflow.eventflow_api.application.event

import com.eventflow.eventflow_api.domain.EventTemplates
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class NewEvent(
    val ownerUserId: UUID, val name: String, val type: String, val startsAt: Instant,
    val endsAt: Instant?, val timezone: String, val location: String?,
    val estimatedCapacity: Int?, val budget: BigDecimal?, val description: String?,
    val reentryAllowed: Boolean
)

interface CreateEventGateway {
    fun save(event: NewEvent, suggestedModules: List<String>): UUID
}

class InvalidEvent(message: String) : RuntimeException(message)

class CreateEvent(private val gateway: CreateEventGateway) {
    fun execute(input: NewEvent): UUID {
        if (input.name.isBlank()) throw InvalidEvent("El nombre es obligatorio")
        if (input.endsAt != null && !input.endsAt.isAfter(input.startsAt))
            throw InvalidEvent("La fecha de fin debe ser posterior al inicio")
        val event = input.copy(name = input.name.trim(), type = input.type.trim().uppercase())
        return gateway.save(event, EventTemplates.modulesFor(event.type))
    }
}
