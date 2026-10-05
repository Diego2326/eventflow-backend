package com.eventflow.eventflow_api.marketplace.application.port

import com.eventflow.eventflow_api.marketplace.domain.MarketplaceOffering
import com.eventflow.eventflow_api.marketplace.domain.OfferingStatus
import com.eventflow.eventflow_api.marketplace.domain.OfferingType
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.math.BigDecimal
import java.util.UUID

interface MarketplaceOfferingRepositoryPort : CrudPort<MarketplaceOffering, UUID> {
    fun findAllByTypeAndStatus(type: OfferingType, status: OfferingStatus): List<MarketplaceOffering>
    fun search(type: OfferingType, category: String?, location: String?, minCapacity: Int?, maxPrice: BigDecimal?, minRating: BigDecimal?, page: Int, pageSize: Int): List<MarketplaceOffering>
    fun findAllByOwnerUserId(ownerUserId: UUID): List<MarketplaceOffering>
}
