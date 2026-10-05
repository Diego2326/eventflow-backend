package com.eventflow.eventflow_api.marketplace.application.port

import com.eventflow.eventflow_api.marketplace.domain.Review
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface ReviewRepositoryPort : CrudPort<Review, UUID> {
    fun existsByReservationIdAndAuthorUserId(reservationId: UUID, authorUserId: UUID): Boolean
    fun averageRatingForOffering(offeringId: UUID): Double?
}
