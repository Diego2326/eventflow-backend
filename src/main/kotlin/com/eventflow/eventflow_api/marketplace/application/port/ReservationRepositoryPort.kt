package com.eventflow.eventflow_api.marketplace.application.port

import com.eventflow.eventflow_api.marketplace.domain.Reservation
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.time.Instant
import java.util.UUID

interface ReservationRepositoryPort : CrudPort<Reservation, UUID> {
    fun findAllByEventId(eventId: UUID): List<Reservation>
    fun hasConflict(offeringId: UUID, startsAt: Instant, endsAt: Instant): Boolean
}
