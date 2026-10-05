package com.eventflow.eventflow_api.marketplace.infrastructure.persistence

import com.eventflow.eventflow_api.marketplace.application.port.MarketplaceOfferingRepositoryPort
import com.eventflow.eventflow_api.marketplace.domain.MarketplaceOffering
import com.eventflow.eventflow_api.marketplace.domain.OfferingStatus
import com.eventflow.eventflow_api.marketplace.domain.OfferingType

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface MarketplaceOfferingRepository : JpaRepository<MarketplaceOffering, UUID>, MarketplaceOfferingRepositoryPort {
    override fun findAllByTypeAndStatus(type: OfferingType, status: OfferingStatus): List<MarketplaceOffering>
    @Query("select o from MarketplaceOffering o where o.type=:type and o.status='ACTIVE' and (:category is null or lower(o.category)=lower(:category)) and (:location is null or lower(o.location) like lower(concat('%',:location,'%'))) and (:minCapacity is null or o.capacity>=:minCapacity) and (:maxPrice is null or o.price<=:maxPrice) and (:minRating is null or o.rating>=:minRating) order by o.createdAt desc, o.id desc")
    fun searchPage(type: OfferingType, category: String?, location: String?, minCapacity: Int?, maxPrice: java.math.BigDecimal?, minRating: java.math.BigDecimal?, pageable: org.springframework.data.domain.Pageable): List<MarketplaceOffering>
    override fun search(type: OfferingType, category: String?, location: String?, minCapacity: Int?, maxPrice: java.math.BigDecimal?, minRating: java.math.BigDecimal?, page:Int, pageSize:Int) =
        searchPage(type,category,location,minCapacity,maxPrice,minRating,org.springframework.data.domain.PageRequest.of(page,pageSize))
    override fun findAllByOwnerUserId(ownerUserId: UUID): List<MarketplaceOffering>
}
