package com.eventflow.eventflow_api.module

import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.exhibition.application.ExhibitionService
import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.invitation.infrastructure.persistence.InvitationRepository
import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.ModuleRecordRequest
import com.eventflow.eventflow_api.module.application.ModuleRecordUpdate
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.session.application.SessionDetailsService
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.transport.application.TransportRouteService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest class ModuleAssociationsIntegrationTest {
    @Autowired lateinit var events:EventService
    @Autowired lateinit var modules:ModuleDataService
    @Autowired lateinit var exhibitions:ExhibitionService
    @Autowired lateinit var sessions:SessionDetailsService
    @Autowired lateinit var routes:TransportRouteService
    @Autowired lateinit var users:UserRepository
    @Autowired lateinit var invitations:InvitationRepository
    @Autowired lateinit var catalog:ModuleCatalogRepository

    @Test fun `stands sessions departures and resources require valid parents`() {
        val owner=user();val guest=user()
        val eventId=events.create(owner,CreateEventRequest("Associations","CUSTOM",Instant.now().plusSeconds(3600))).id
        listOf("MAP","EXH","SES","TRN","RSC").forEach{code->
            catalog.save(ModuleCatalog(code,code,"Guest",code));events.configureModule(owner,eventId,code,ConfigureModuleRequest())
        }
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        invitations.save(Invitation(eventId=eventId,linkedUserId=guest,guestName="Guest",tokenHash=UUID.randomUUID().toString()))
        val exhibitor=modules.create(owner,eventId,"EXH","EXHIBITOR",ModuleRecordRequest(title="Company"))
        val point=modules.create(owner,eventId,"MAP","POINT",ModuleRecordRequest(title="Hall A"))
        assertThrows(BadRequestException::class.java){modules.create(owner,eventId,"EXH","STAND",ModuleRecordRequest(title="A1"))}
        val stand=modules.create(owner,eventId,"EXH","STAND",ModuleRecordRequest(title="A1",parentRecordId=exhibitor.id,payload=mapOf("mapPointId" to point.id.toString())))
        val resource=modules.create(owner,eventId,"RSC","RESOURCE",ModuleRecordRequest(title="Catalog",payload=mapOf("sourceRecordId" to stand.id.toString(),"url" to "https://example.org/catalog")))
        assertThrows(BadRequestException::class.java){modules.create(owner,eventId,"RSC","RESOURCE",ModuleRecordRequest(title="Unsafe",payload=mapOf("url" to "javascript:alert(1)")))}
        assertThrows(BadRequestException::class.java){modules.create(owner,eventId,"RSC","RESOURCE",ModuleRecordRequest(title="Missing",payload=mapOf("fileId" to UUID.randomUUID().toString())))}
        assertEquals(listOf(stand.id),exhibitions.details(guest,eventId,exhibitor.id).stands.map{it.id})
        assertEquals(listOf(resource.id),exhibitions.details(guest,eventId,exhibitor.id).resources.map{it.id})
        assertThrows(ConflictException::class.java){modules.archive(owner,eventId,stand.id)}
        assertThrows(ConflictException::class.java){modules.update(owner,eventId,stand.id,ModuleRecordUpdate(status="CLOSED"))}
        val speaker=modules.create(owner,eventId,"SES","SPEAKER",ModuleRecordRequest(title="Speaker"))
        val session=modules.create(owner,eventId,"SES","SESSION",ModuleRecordRequest(title="Talk",parentRecordId=speaker.id))
        assertEquals(speaker.id,sessions.details(guest,eventId,session.id).speaker.id)
        assertEquals(stand.id,modules.mapSearch(guest,eventId,"A1").single().id)
        assertEquals(session.id,modules.mapSearch(guest,eventId,"Talk").single().id)
        val route=modules.create(owner,eventId,"TRN","ROUTE",ModuleRecordRequest(title="Airport",payload=mapOf("origin" to "Venue", "destination" to "Airport", "meetingPoint" to "North gate")))
        assertThrows(BadRequestException::class.java){modules.create(owner,eventId,"TRN","ROUTE",ModuleRecordRequest(title="Incomplete"))}
        val departure=modules.create(owner,eventId,"TRN","DEPARTURE",ModuleRecordRequest(title="Bus",parentRecordId=route.id,
            capacity=20,startsAt=Instant.now().plusSeconds(1800),endsAt=Instant.now().plusSeconds(3600)))
        assertEquals(listOf(departure.id),routes.details(guest,eventId,route.id).departures.map{it.id})
        assertThrows(BadRequestException::class.java){modules.update(owner,eventId,resource.id,ModuleRecordUpdate(payload=mapOf("sourceRecordId" to UUID.randomUUID().toString())))}
    }

    private fun user()=requireNotNull(users.save(User(name="Association user",email="assoc-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
}
