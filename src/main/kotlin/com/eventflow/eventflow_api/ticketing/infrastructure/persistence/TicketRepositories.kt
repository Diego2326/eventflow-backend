package com.eventflow.eventflow_api.ticketing.infrastructure.persistence

import com.eventflow.eventflow_api.ticketing.application.port.*
import com.eventflow.eventflow_api.ticketing.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface TicketTypeRepository : JpaRepository<TicketType, UUID>, TicketTypePort
interface TicketOrderRepository : JpaRepository<TicketOrder, UUID>, TicketOrderPort
interface EventTicketRepository : JpaRepository<EventTicket, UUID>, EventTicketPort {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EventTicket t where t.id=:id")
    override fun findLocked(id: UUID): EventTicket?
}
interface TicketAccessLogRepository : JpaRepository<TicketAccessLog, UUID>, TicketAccessLogPort
interface TicketSettingsAuditRepository : JpaRepository<TicketSettingsAudit, UUID>, TicketSettingsAuditPort
