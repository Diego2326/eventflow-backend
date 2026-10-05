package com.eventflow.eventflow_api.module

import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.invitation.infrastructure.persistence.InvitationRepository
import com.eventflow.eventflow_api.module.application.ModuleActionRequest
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

@SpringBootTest
class ExtensionRulesIntegrationTest {
    @Autowired lateinit var events: EventService
    @Autowired lateinit var modules: ModuleDataService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var catalog: ModuleCatalogRepository
    @Autowired lateinit var invitations: InvitationRepository

    @Test fun `queue position and rejoining follow active membership`() {
        val (owner, guests, eventId) = event("QUE", 2)
        val queue=modules.create(owner,eventId,"QUE","QUEUE",ModuleRecordRequest(title="Photos",capacity=2))
        modules.action(guests[0],eventId,queue.id,ModuleActionRequest("JOIN"))
        modules.action(guests[1],eventId,queue.id,ModuleActionRequest("JOIN"))
        assertEquals(2,modules.queuePosition(guests[1],eventId,queue.id).position)
        assertThrows(ConflictException::class.java){modules.action(guests[1],eventId,queue.id,ModuleActionRequest("JOIN"))}
        modules.action(guests[0],eventId,queue.id,ModuleActionRequest("LEAVE"))
        assertEquals(1,modules.queuePosition(guests[1],eventId,queue.id).position)
        modules.action(guests[0],eventId,queue.id,ModuleActionRequest("JOIN"))
        assertEquals(2,modules.queuePosition(guests[0],eventId,queue.id).position)
    }

    @Test fun `activity reserves one place and releases it on cancellation`() {
        val (owner, guests, eventId) = event("BKG", 2)
        val activity=modules.create(owner,eventId,"BKG","ACTIVITY",ModuleRecordRequest(title="Workshop",capacity=1,
            startsAt=Instant.now().plusSeconds(1800),endsAt=Instant.now().plusSeconds(3600)))
        modules.action(guests[0],eventId,activity.id,ModuleActionRequest("RESERVE"))
        assertThrows(ConflictException::class.java){modules.action(guests[1],eventId,activity.id,ModuleActionRequest("RESERVE"))}
        modules.action(guests[0],eventId,activity.id,ModuleActionRequest("CANCEL"))
        modules.action(guests[1],eventId,activity.id,ModuleActionRequest("RESERVE"))
        assertEquals(1,modules.get(owner,eventId,activity.id).currentCount)
    }

    private fun event(code:String, guestCount:Int):Triple<UUID,List<UUID>,UUID>{
        val owner=user()
        val guests=List(guestCount){user()}
        val id=events.create(owner,CreateEventRequest("Extension","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog(code,code,"Guest",code))
        events.configureModule(owner,id,code,ConfigureModuleRequest())
        events.transition(owner,id,EventStatus.PUBLISHED)
        guests.forEach{invitations.save(Invitation(eventId=id,linkedUserId=it,guestName="Guest",tokenHash=UUID.randomUUID().toString()))}
        return Triple(owner,guests,id)
    }
    private fun user()=requireNotNull(users.save(User(name="Extension user",email="ext-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
}
