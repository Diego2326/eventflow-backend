package com.eventflow.eventflow_api.marketplace.infrastructure.persistence

import com.eventflow.eventflow_api.marketplace.application.port.SimulatedPaymentRepositoryPort
import com.eventflow.eventflow_api.marketplace.domain.SimulatedPayment

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SimulatedPaymentRepository : JpaRepository<SimulatedPayment, UUID>, SimulatedPaymentRepositoryPort { override fun findAllByReservationId(reservationId: UUID): List<SimulatedPayment> }
