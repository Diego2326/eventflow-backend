package com.eventflow.eventflow_api.marketplace

import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.marketplace.application.AvailabilityRequest
import com.eventflow.eventflow_api.marketplace.application.DecisionRequest
import com.eventflow.eventflow_api.marketplace.application.MarketplaceService
import com.eventflow.eventflow_api.marketplace.application.OfferingRequest
import com.eventflow.eventflow_api.marketplace.application.PaymentRequest
import com.eventflow.eventflow_api.marketplace.application.ReservationRequest
import com.eventflow.eventflow_api.marketplace.application.ReviewRequest
import com.eventflow.eventflow_api.marketplace.domain.OfferingType
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException

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

    @Test fun `feature availability and computed rating filter offerings`() {
        val provider=user();val client=user()
        val event=events.create(client,CreateEventRequest("Filter event","CUSTOM",Instant.now().plusSeconds(3600))).id
        val offering=service.create(provider,OfferingRequest(OfferingType.SERVICE,"Catering",price=BigDecimal.TEN,
            attributes="{\"features\":[\"vegan\"]}"))
        val id=requireNotNull(offering.id)
        val first=Instant.now().plusSeconds(7200)
        val reservation=service.reserve(client,ReservationRequest(event,id,first,first.plusSeconds(3600)))
        service.decide(provider,requireNotNull(reservation.id),DecisionRequest(true))
        service.complete(provider,requireNotNull(reservation.id))
        service.review(client,requireNotNull(reservation.id),ReviewRequest(5))
        assertEquals(listOf(id),service.search(OfferingType.SERVICE,null,null,null,null,
            minRating=BigDecimal(4),feature="vegan").map{it.id})
        assertTrue(service.search(OfferingType.SERVICE,null,null,null,null,feature="wifi").none{it.id==id})
        val blocked=first.plusSeconds(7200)
        val next=service.reserve(client,ReservationRequest(event,id,blocked,blocked.plusSeconds(3600)))
        service.decide(provider,requireNotNull(next.id),DecisionRequest(true))
        assertTrue(service.search(OfferingType.SERVICE,null,null,null,null,startsAt=blocked,endsAt=blocked.plusSeconds(1800)).none{it.id==id})
    }

    private fun user() = requireNotNull(users.save(User(name = "Tester", email = "market-${UUID.randomUUID()}@example.com", passwordHash = "unused")).id)
}
