package com.eventflow.eventflow_api.marketplace.application.port

import com.eventflow.eventflow_api.marketplace.domain.OfferingAvailability
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface OfferingAvailabilityRepositoryPort : CrudPort<OfferingAvailability, UUID> {
    fun findAllByOfferingId(offeringId: UUID): List<OfferingAvailability>
}
