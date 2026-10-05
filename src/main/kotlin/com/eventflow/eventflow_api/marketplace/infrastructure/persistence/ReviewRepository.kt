package com.eventflow.eventflow_api.marketplace.infrastructure.persistence

import com.eventflow.eventflow_api.marketplace.application.port.ReviewRepositoryPort
import com.eventflow.eventflow_api.marketplace.domain.Review

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ReviewRepository : JpaRepository<Review, UUID>, ReviewRepositoryPort {
    override fun existsByReservationIdAndAuthorUserId(reservationId: UUID, authorUserId: UUID): Boolean
    @Query("select avg(r.rating) from Review r, Reservation res where r.reservationId=res.id and res.offeringId=:offeringId")
    override fun averageRatingForOffering(offeringId: UUID): Double?
}
