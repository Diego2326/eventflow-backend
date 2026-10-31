package com.eventflow.eventflow_api.marketplace.infrastructure.persistence

import com.eventflow.eventflow_api.marketplace.application.port.MarketplaceOfferingRepositoryPort
import com.eventflow.eventflow_api.marketplace.domain.MarketplaceOffering
import com.eventflow.eventflow_api.marketplace.domain.OfferingStatus
import com.eventflow.eventflow_api.marketplace.domain.OfferingType

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import jakarta.persistence.criteria.Predicate
import java.util.UUID

interface MarketplaceOfferingRepository : JpaRepository<MarketplaceOffering, UUID>, JpaSpecificationExecutor<MarketplaceOffering>, MarketplaceOfferingRepositoryPort {
    override fun findAllByTypeAndStatus(type: OfferingType, status: OfferingStatus): List<MarketplaceOffering>
    override fun search(type: OfferingType, category: String?, location: String?, minCapacity: Int?, maxPrice: java.math.BigDecimal?, minRating: java.math.BigDecimal?, page:Int, pageSize:Int): List<MarketplaceOffering> {
        // Omit absent predicates instead of binding untyped nulls inside lower().
        val specification = Specification<MarketplaceOffering> { root, _, builder ->
            val predicates = mutableListOf<Predicate>(
                builder.equal(root.get<OfferingType>("type"), type),
                builder.equal(root.get<OfferingStatus>("status"), OfferingStatus.ACTIVE)
            )
            category?.let { predicates += builder.equal(builder.lower(root.get("category")), builder.lower(builder.literal(it))) }
            location?.let { predicates += builder.like(builder.lower(root.get("location")), builder.lower(builder.literal("%$it%"))) }
            minCapacity?.let { predicates += builder.ge(root.get<Int>("capacity"), it) }
            maxPrice?.let { predicates += builder.le(root.get<java.math.BigDecimal>("price"), it) }
            minRating?.let { predicates += builder.ge(root.get<java.math.BigDecimal>("rating"), it) }
            builder.and(*predicates.toTypedArray())
        }
        val sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        return findAll(specification, PageRequest.of(page, pageSize, sort)).content
    }
    override fun findAllByOwnerUserId(ownerUserId: UUID): List<MarketplaceOffering>
}
