package com.eventflow.eventflow_api.marketplace.application.port

import com.eventflow.eventflow_api.marketplace.domain.SimulatedPayment
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface SimulatedPaymentRepositoryPort : CrudPort<SimulatedPayment, UUID> {
    fun findAllByReservationId(reservationId: UUID): List<SimulatedPayment>
}
