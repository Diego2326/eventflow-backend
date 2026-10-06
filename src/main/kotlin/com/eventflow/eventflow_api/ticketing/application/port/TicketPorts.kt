package com.eventflow.eventflow_api.ticketing.application.port

import com.eventflow.eventflow_api.shared.application.port.CrudPort
import com.eventflow.eventflow_api.ticketing.domain.*
import java.util.UUID

interface TicketTypePort : CrudPort<TicketType, UUID> {
    fun findAllByEventId(eventId: UUID): List<TicketType>
}
interface TicketOrderPort : CrudPort<TicketOrder, UUID> {
    fun findByBuyerUserIdAndIdempotencyKey(buyerUserId: UUID, idempotencyKey: String): TicketOrder?
    fun findAllByBuyerUserIdOrderByCreatedAtDesc(buyerUserId: UUID): List<TicketOrder>
    fun findAllByEventIdOrderByCreatedAtDesc(eventId: UUID): List<TicketOrder>
}
interface EventTicketPort : CrudPort<EventTicket, UUID> {
    fun findAllByEventId(eventId: UUID): List<EventTicket>
    fun findAllByTicketTypeId(ticketTypeId: UUID): List<EventTicket>
    fun findAllByOrderId(orderId: UUID): List<EventTicket>
    fun findByQrHash(qrHash: String): EventTicket?
    fun findLocked(id: UUID): EventTicket?
}
interface TicketAccessLogPort : CrudPort<TicketAccessLog, UUID> {
    fun findAllByTicketIdOrderByCreatedAt(ticketId: UUID): List<TicketAccessLog>
}
interface TicketSettingsAuditPort : CrudPort<TicketSettingsAudit, UUID> {
    fun findAllByEventIdOrderByCreatedAtDesc(eventId: UUID): List<TicketSettingsAudit>
}
