package com.eventflow.eventflow_api

import com.eventflow.eventflow_api.application.event.CreateEventRequest
import com.eventflow.eventflow_api.application.event.EventService
import com.eventflow.eventflow_api.application.marketplace.*
import com.eventflow.eventflow_api.auth.model.User
import com.eventflow.eventflow_api.common.BadRequestException
import com.eventflow.eventflow_api.common.ConflictException
import com.eventflow.eventflow_api.common.ForbiddenException
import com.eventflow.eventflow_api.domain.OfferingType
import com.eventflow.eventflow_api.infrastructure.persistence.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
class MarketplaceRulesIntegrationTest {
    @Autowired lateinit var service: MarketplaceService
    @Autowired lateinit var events: EventService
    @Autowired lateinit var users: UserRepository

    @Test fun `search is filtered and bounded, reservations honor unavailable ranges and payments need acceptance`() {
        val provider = user()
        val client = user()
        val event = events.create(client, CreateEventRequest("Market", "CUSTOM", Instant.now().plusSeconds(3600))).id
        val offering = service.create(provider, OfferingRequest(OfferingType.SPACE, "Sala", "Venue", location = "Centro", capacity = 50, price = BigDecimal.TEN))
        val id = requireNotNull(offering.id)
        assertEquals(listOf(id), service.search(OfferingType.SPACE, "venue", "cent", 20, BigDecimal.TEN).map { it.id })
        assertTrue(service.search(OfferingType.SPACE, "venue", "cent", 51, BigDecimal.TEN).isEmpty())
        assertThrows(BadRequestException::class.java) { service.search(OfferingType.SPACE, null, null, null, null, 101) }
        val start = Instant.now().plusSeconds(7200)
        val end = start.plusSeconds(3600)
        service.availability(provider, id, AvailabilityRequest(start, end, false))
        assertThrows(ConflictException::class.java) { service.reserve(client, ReservationRequest(event, id, start, end)) }
        val later = end.plusSeconds(3600)
        val reservation = service.reserve(client, ReservationRequest(event, id, later, later.plusSeconds(3600)))
        val reservationId = requireNotNull(reservation.id)
        assertThrows(ConflictException::class.java) { service.payment(client, reservationId, PaymentRequest(BigDecimal.TEN)) }
        service.decide(provider, reservationId, DecisionRequest(true))
        assertThrows(BadRequestException::class.java) { service.payment(client, reservationId, PaymentRequest(BigDecimal.TEN, "REFUNDED")) }
        val payment = service.payment(client, reservationId, PaymentRequest(BigDecimal.TEN))
        assertTrue(service.receipt(client, reservationId, requireNotNull(payment.id)).notice.contains("SIMULACIÓN ACADÉMICA"))
        assertThrows(ForbiddenException::class.java) { service.receipt(user(), reservationId, requireNotNull(payment.id)) }
        assertThrows(ConflictException::class.java) { service.payment(client, reservationId, PaymentRequest(BigDecimal.TEN)) }
    }

    private fun user() = requireNotNull(users.save(User(name = "Tester", email = "market-${UUID.randomUUID()}@example.com", passwordHash = "unused")).id)
}
