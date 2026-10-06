package com.eventflow.eventflow_api.ticketing.infrastructure.web

import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import com.eventflow.eventflow_api.ticketing.application.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController @RequestMapping("/api")
class TicketingController(private val service: TicketingService) {
    @GetMapping("/public/events") fun catalog(@RequestParam(required = false) q: String?, @RequestParam(required = false) type: String?,
        @RequestParam(required = false) from: Instant?, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int) = service.catalog(q, type, from, page, size)
    @GetMapping("/public/events/{eventId}") fun publicEvent(@PathVariable eventId: UUID) = service.publicEvent(eventId)
    @GetMapping("/events/{eventId}/ticket-settings") fun settings(a: Authentication, @PathVariable eventId: UUID) = service.settings(a.userId(), eventId)
    @PutMapping("/events/{eventId}/ticket-settings") fun settings(a: Authentication, @PathVariable eventId: UUID,
        @RequestBody r: TicketSettingsRequest) = service.settings(a.userId(), eventId, r)
    @PostMapping("/events/{eventId}/ticket-types") @ResponseStatus(HttpStatus.CREATED)
    fun createType(a: Authentication, @PathVariable eventId: UUID, @RequestBody r: TicketTypeRequest) = service.createType(a.userId(), eventId, r)
    @PutMapping("/events/{eventId}/ticket-types/{typeId}") fun updateType(a: Authentication, @PathVariable eventId: UUID,
        @PathVariable typeId: UUID, @RequestBody r: TicketTypeRequest) = service.updateType(a.userId(), eventId, typeId, r)
    @GetMapping("/events/{eventId}/ticket-types") fun types(a: Authentication, @PathVariable eventId: UUID) = service.listTypes(a.userId(), eventId)
    @PostMapping("/events/{eventId}/ticket-orders") @ResponseStatus(HttpStatus.CREATED)
    fun buy(a: Authentication, @PathVariable eventId: UUID, @RequestHeader("Idempotency-Key") key: String,
        @RequestBody r: BuyTicketsRequest) = service.buy(a.userId(), eventId, key, r)
    @GetMapping("/ticket-orders/mine") fun mine(a: Authentication) = service.mine(a.userId())
    @GetMapping("/ticket-orders/{orderId}") fun myOrder(a: Authentication, @PathVariable orderId: UUID) = service.myOrder(a.userId(), orderId)
    @PostMapping("/ticket-orders/{orderId}/cancel") fun cancel(a: Authentication, @PathVariable orderId: UUID) = service.cancelOrder(a.userId(), orderId)
    @GetMapping("/events/{eventId}/ticket-orders") fun eventOrders(a: Authentication, @PathVariable eventId: UUID,
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = service.eventOrders(a.userId(), eventId, page, size)
    @PostMapping("/events/{eventId}/ticket-orders/{orderId}/cancel") fun cancelEventOrder(a: Authentication,
        @PathVariable eventId: UUID, @PathVariable orderId: UUID, @RequestBody r: TicketRevokeRequest) =
        service.cancelEventOrder(a.userId(), eventId, orderId, r)
    @GetMapping("/events/{eventId}/ticket-sales") fun sales(a: Authentication, @PathVariable eventId: UUID) = service.sales(a.userId(), eventId)
    @PostMapping("/events/{eventId}/tickets/{ticketId}/revoke") fun revoke(a: Authentication, @PathVariable eventId: UUID,
        @PathVariable ticketId: UUID, @RequestBody r: TicketRevokeRequest) = service.revoke(a.userId(), eventId, ticketId, r)
    @PostMapping("/tickets/{ticketId}/regenerate") fun regenerate(a: Authentication, @PathVariable ticketId: UUID) = service.regenerate(a.userId(), ticketId)
}
