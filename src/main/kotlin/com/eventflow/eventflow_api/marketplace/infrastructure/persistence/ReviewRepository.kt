package com.eventflow.eventflow_api.marketplace.infrastructure.persistence

import com.eventflow.eventflow_api.marketplace.application.port.ReviewRepositoryPort
import com.eventflow.eventflow_api.marketplace.domain.Review

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReviewRepository : JpaRepository<Review, UUID>, ReviewRepositoryPort { override fun existsByReservationIdAndAuthorUserId(reservationId: UUID, authorUserId: UUID): Boolean }
