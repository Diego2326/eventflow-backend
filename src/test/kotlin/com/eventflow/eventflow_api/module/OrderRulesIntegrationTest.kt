package com.eventflow.eventflow_api.module

import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.invitation.infrastructure.persistence.InvitationRepository
import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.ModuleRecordRequest
import com.eventflow.eventflow_api.module.application.ModuleRecordUpdate
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.notification.infrastructure.persistence.NotificationRepository
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest
class OrderRulesIntegrationTest {
    @Autowired lateinit var events: EventService
    @Autowired lateinit var modules: ModuleDataService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var catalog: ModuleCatalogRepository
    @Autowired lateinit var invitations: InvitationRepository
    @Autowired lateinit var notifications: NotificationRepository

    @Test fun `orders validate stock and follow controlled transitions`() {
        val owner=user()
        val guest=user()
        val eventId=events.create(owner,CreateEventRequest("Orders","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("ORD","Orders","Guest","Orders"))
        catalog.save(ModuleCatalog("NOT","Notifications","Guest","Notifications"))
        events.configureModule(owner,eventId,"ORD",ConfigureModuleRequest())
        events.configureModule(owner,eventId,"NOT",ConfigureModuleRequest())
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        invitations.save(Invitation(eventId=eventId,linkedUserId=guest,guestName="Guest",tokenHash=UUID.randomUUID().toString()))
        val item=modules.create(owner,eventId,"ORD","MENU_ITEM",ModuleRecordRequest(title="Water",payload=mapOf("price" to 5,"available" to true),capacity=2))
        val payload=mapOf("items" to listOf(mapOf("itemId" to item.id.toString(),"quantity" to 2)))
        val order=modules.create(guest,eventId,"ORD","ORDER",ModuleRecordRequest(payload=payload))
        assertEquals("PENDING",order.status)
        assertEquals(10.0,order.payload["total"].toString().toDouble())
        assertEquals(2,modules.get(owner,eventId,item.id).currentCount)
        assertThrows(ConflictException::class.java) { modules.create(guest,eventId,"ORD","ORDER",ModuleRecordRequest(payload=payload)) }
        assertThrows(ForbiddenException::class.java) { modules.update(guest,eventId,order.id,ModuleRecordUpdate(status="DELIVERED")) }
        assertEquals("CANCELLED",modules.update(guest,eventId,order.id,ModuleRecordUpdate(status="CANCELLED")).status)
        assertEquals(0,modules.get(owner,eventId,item.id).currentCount)
        val next=modules.create(guest,eventId,"ORD","ORDER",ModuleRecordRequest(payload=payload))
        assertEquals("ACCEPTED",modules.update(owner,eventId,next.id,ModuleRecordUpdate(status="ACCEPTED")).status)
        assertEquals("PREPARING",modules.update(owner,eventId,next.id,ModuleRecordUpdate(status="PREPARING")).status)
        assertEquals("READY",modules.update(owner,eventId,next.id,ModuleRecordUpdate(status="READY")).status)
        assertEquals("DELIVERED",modules.update(owner,eventId,next.id,ModuleRecordUpdate(status="DELIVERED")).status)
        assertTrue(notifications.findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId).any{it.recipientUserId==guest&&it.title.contains("entregado")})
    }

    private fun user()=requireNotNull(users.save(User(name="Order user",email="order-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
}
