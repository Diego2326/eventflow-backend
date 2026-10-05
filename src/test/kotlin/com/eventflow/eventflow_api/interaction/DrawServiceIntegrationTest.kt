package com.eventflow.eventflow_api.interaction

import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.interaction.application.DrawService
import com.eventflow.eventflow_api.invitation.domain.GuestAccessLog
import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.invitation.infrastructure.persistence.GuestAccessLogRepository
import com.eventflow.eventflow_api.invitation.infrastructure.persistence.InvitationRepository
import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.ModuleRecordRequest
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest class DrawServiceIntegrationTest {
    @Autowired lateinit var events:EventService
    @Autowired lateinit var draws:DrawService
    @Autowired lateinit var modules:ModuleDataService
    @Autowired lateinit var users:UserRepository
    @Autowired lateinit var catalog:ModuleCatalogRepository
    @Autowired lateinit var invitations:InvitationRepository
    @Autowired lateinit var logs:GuestAccessLogRepository

    @Test fun `draw only picks checked in guests and excludes previous winner`() {
        val owner=requireNotNull(users.save(User(name="Owner",email="draw-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
        val eventId=events.create(owner,CreateEventRequest("Draw","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("INT","Interactions","Guest","Draws"))
        events.configureModule(owner,eventId,"INT",ConfigureModuleRequest())
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        val eligible=invitations.save(Invitation(eventId=eventId,guestName="Eligible",tokenHash=UUID.randomUUID().toString()))
        invitations.save(Invitation(eventId=eventId,guestName="Absent",tokenHash=UUID.randomUUID().toString()))
        logs.save(GuestAccessLog(invitationId=requireNotNull(eligible.id),action="CHECK_IN"))
        val first=modules.create(owner,eventId,"INT","DRAW",ModuleRecordRequest(title="First"))
        val second=modules.create(owner,eventId,"INT","DRAW",ModuleRecordRequest(title="Second"))
        assertEquals(eligible.id,draws.run(owner,eventId,first.id).invitationId)
        assertThrows(ConflictException::class.java){draws.run(owner,eventId,first.id)}
        assertThrows(ConflictException::class.java){draws.run(owner,eventId,second.id)}
    }
}
