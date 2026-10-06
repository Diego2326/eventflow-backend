package com.eventflow.eventflow_api.networking

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
import com.eventflow.eventflow_api.networking.application.MeetingRequest
import com.eventflow.eventflow_api.networking.application.MeetingService
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest class MeetingServiceIntegrationTest {
    @Autowired lateinit var events:EventService
    @Autowired lateinit var modules:ModuleDataService
    @Autowired lateinit var meetings:MeetingService
    @Autowired lateinit var users:UserRepository
    @Autowired lateinit var invitations:InvitationRepository
    @Autowired lateinit var catalog:ModuleCatalogRepository

    @Test fun `only participants see a meeting and accepted times cannot overlap`() {
        val owner=user();val a=user();val b=user();val c=user()
        val eventId=events.create(owner,CreateEventRequest("Networking","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("NET","Networking","Guest","Profiles"))
        events.configureModule(owner,eventId,"NET",ConfigureModuleRequest())
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        listOf(a,b,c).forEach{invitations.save(Invitation(eventId=eventId,linkedUserId=it,guestName="Guest",tokenHash=UUID.randomUUID().toString()))}
        listOf(a,b,c).forEach{modules.create(it,eventId,"NET","PROFILE",ModuleRecordRequest(payload=mapOf("consent" to true,"visible" to true,"interests" to listOf("tech"))))}
        val start=Instant.now().plusSeconds(1800)
        val first=meetings.request(a,eventId,MeetingRequest(b,start,start.plusSeconds(900)))
        assertThrows(BadRequestException::class.java){modules.update(owner,eventId,first.id,ModuleRecordUpdate(status="ACCEPTED"))}
        assertThrows(NotFoundException::class.java){modules.get(c,eventId,first.id)}
        meetings.decide(b,eventId,first.id,true)
        val second=meetings.request(c,eventId,MeetingRequest(b,start.plusSeconds(300),start.plusSeconds(1200)))
        assertThrows(ConflictException::class.java){meetings.decide(b,eventId,second.id,true)}
    }
    private fun user()=requireNotNull(users.save(User(name="Network user",email="network-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
}
