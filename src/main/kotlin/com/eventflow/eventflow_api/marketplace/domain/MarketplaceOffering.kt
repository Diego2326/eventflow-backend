package com.eventflow.eventflow_api.marketplace.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "marketplace_offering")
class MarketplaceOffering(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "offering_id") var id: UUID? = null,
    @Column(name = "owner_user_id", nullable = false) var ownerUserId: UUID,
    @Enumerated(EnumType.STRING) @Column(name = "offering_type", nullable = false) var type: OfferingType,
    @Column(nullable = false) var name: String,
    var category: String? = null,
    @Column(columnDefinition = "text") var description: String? = null,
    var location: String? = null,
    var capacity: Int? = null,
    @Column(nullable = false) var price: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, columnDefinition = "text") var attributes: String = "{}",
    @Column(name = "image_urls", nullable = false, columnDefinition = "text") var imageUrls: String = "[]",
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: OfferingStatus = OfferingStatus.ACTIVE,
    @Column(nullable = false) var rating: BigDecimal = BigDecimal.ZERO,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
