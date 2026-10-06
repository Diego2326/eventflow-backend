package com.eventflow.eventflow_api.ticketing

import com.eventflow.eventflow_api.auth.application.AuthService
import com.eventflow.eventflow_api.auth.application.dto.LoginRequest
import com.eventflow.eventflow_api.auth.application.dto.RegisterRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.event.domain.EventVisibility
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.ticketing.application.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
class TicketingFlowTest {
    @Autowired lateinit var auth: AuthService
    @Autowired lateinit var events: EventService
    @Autowired lateinit var ticketing: TicketingService
    @Autowired lateinit var moduleCatalog: ModuleCatalogRepository

    private fun user(label: String): UUID {
        val email = "${label}-${UUID.randomUUID()}@example.com"
        val token = requireNotNull(auth.register(RegisterRequest(name = label, email = email,
            password = "Strong#Pass1", phone = "+50255550003")))
        auth.verifyEmail(token)
        return auth.login(LoginRequest(email, "Strong#Pass1"), "test", "127.0.0.1").userId
    }

    @Test fun `public simulated purchase issues passes and enforces capacity and access`() {
        val organizer = user("Organizer")
        val buyer = user("Buyer")
        val other = user("Other")
        val starts = Instant.now().plusSeconds(86400)
        val ends = starts.plusSeconds(14400)
        val event = events.create(organizer, CreateEventRequest("Convención", "CUSTOM", starts,
            ends, estimatedCapacity = 10))
        if (!moduleCatalog.existsById("GST")) moduleCatalog.save(ModuleCatalog("GST", "Pases", "Acceso", "Pases y check-in"))
        events.configureModule(organizer, event.id, "GST", ConfigureModuleRequest())
        events.transition(organizer, event.id, EventStatus.PUBLISHED)
        ticketing.settings(organizer, event.id, TicketSettingsRequest(EventVisibility.PUBLIC, admissionCapacity = 2))
        val type = ticketing.createType(organizer, event.id, TicketTypeRequest("General", price = BigDecimal("50.00"),
            capacity = 2, maxPerOrder = 2, salesStart = Instant.now().minusSeconds(60), salesEnd = ends))
        assertEquals(1, ticketing.catalog(null, null, null, 0, 20).count { it.id == event.id })
        val request = BuyTicketsRequest(listOf(TicketLineRequest(type.id, 2)))
        val purchaseKey = "buy-${UUID.randomUUID()}"
        val order = ticketing.buy(buyer, event.id, purchaseKey, request)
        assertEquals(order.id, ticketing.buy(buyer, event.id, purchaseKey, request).id)
        assertEquals(0, ticketing.publicEvent(event.id).ticketTypes.single().available)
        assertEquals(BigDecimal("100.00"), order.total)
        assertEquals(2, order.tickets.size)
        assertNotEquals(order.tickets[0].qrPayload, order.tickets[1].qrPayload)
        assertEquals(event.id, events.get(buyer, event.id).id)
        assertThrows(ConflictException::class.java) {
            ticketing.buy(other, event.id, "buy-${UUID.randomUUID()}", BuyTicketsRequest(listOf(TicketLineRequest(type.id, 1))))
        }
        val qr = requireNotNull(order.tickets.first().qrPayload)
        assertTrue(ticketing.checkQr(organizer, event.id, qr, true).ticket.checkedIn)
        assertThrows(ConflictException::class.java) { ticketing.checkQr(organizer, event.id, qr, true) }
        assertThrows(ConflictException::class.java) { ticketing.cancelOrder(buyer, order.id) }
        ticketing.settings(organizer, event.id, TicketSettingsRequest(EventVisibility.PUBLIC,
            admissionCapacity = 2, allowRevokeAfterCheckIn = true))
        assertEquals("REVOKED", ticketing.revoke(organizer, event.id, order.tickets.first().id,
            TicketRevokeRequest("Acceso retirado")).status)
        assertThrows(ConflictException::class.java) { ticketing.checkQr(organizer, event.id, qr, true) }
        assertThrows(ConflictException::class.java) {
            ticketing.buy(other, event.id, "buy-${UUID.randomUUID()}", BuyTicketsRequest(listOf(TicketLineRequest(type.id, 1))))
        }
        assertEquals(null, ticketing.eventOrders(organizer, event.id, 0, 20).single().tickets.first().qrPayload)
        assertEquals(1, ticketing.sales(organizer, event.id).activeTickets)
    }
}
