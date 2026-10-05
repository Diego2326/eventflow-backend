package com.eventflow.eventflow_api.marketplace.infrastructure.persistence

import com.eventflow.eventflow_api.marketplace.application.port.ReservationRepositoryPort
import com.eventflow.eventflow_api.marketplace.domain.Reservation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface ReservationRepository : JpaRepository<Reservation, UUID>, ReservationRepositoryPort {
    override fun findAllByEventId(eventId: UUID): List<Reservation>
    @Query("select count(r)>0 from Reservation r where r.offeringId=:offeringId and r.status='ACCEPTED' and r.startsAt<:endsAt and r.endsAt>:startsAt")
    override fun hasConflict(offeringId: UUID, startsAt: Instant, endsAt: Instant): Boolean
}
