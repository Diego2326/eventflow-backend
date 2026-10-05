package com.eventflow.eventflow_api.marketplace.infrastructure.persistence

import com.eventflow.eventflow_api.marketplace.application.port.OfferingAvailabilityRepositoryPort
import com.eventflow.eventflow_api.marketplace.domain.OfferingAvailability

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OfferingAvailabilityRepository : JpaRepository<OfferingAvailability, UUID>, OfferingAvailabilityRepositoryPort { override fun findAllByOfferingId(offeringId: UUID): List<OfferingAvailability> }
