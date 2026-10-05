package com.eventflow.eventflow_api

import com.eventflow.eventflow_api.application.event.ConfigureModuleRequest
import com.eventflow.eventflow_api.application.event.CreateEventRequest
import com.eventflow.eventflow_api.application.event.EventService
import com.eventflow.eventflow_api.application.modules.*
import com.eventflow.eventflow_api.auth.model.User
import com.eventflow.eventflow_api.common.ConflictException
import com.eventflow.eventflow_api.common.ForbiddenException
import com.eventflow.eventflow_api.common.NotFoundException
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.infrastructure.persistence.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest
class PollRulesIntegrationTest {
    @Autowired lateinit var events: EventService
    @Autowired lateinit var modules: ModuleDataService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var catalog: ModuleCatalogRepository
    @Autowired lateinit var invitations: InvitationRepository

    @Test fun `multiple poll votes are per option and results remain private until published`() {
        val owner=user()
        val guest=user()
        val eventId=events.create(owner,CreateEventRequest("Poll","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("INT","Interactions","Guest","Polls"))
        events.configureModule(owner,eventId,"INT",ConfigureModuleRequest())
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        invitations.save(Invitation(eventId=eventId,linkedUserId=guest,guestName="Guest",tokenHash=UUID.randomUUID().toString()))
        val options=listOf(mapOf("id" to "a","label" to "A"),mapOf("id" to "b","label" to "B"))
        val poll=modules.create(owner,eventId,"INT","POLL",ModuleRecordRequest(title="Vote",payload=mapOf("options" to options,"allowMultiple" to true)))
        modules.action(guest,eventId,poll.id,ModuleActionRequest("VOTE",mapOf("optionId" to "a")))
        modules.action(guest,eventId,poll.id,ModuleActionRequest("VOTE",mapOf("optionId" to "b")))
        assertThrows(ConflictException::class.java) { modules.action(guest,eventId,poll.id,ModuleActionRequest("VOTE",mapOf("optionId" to "a"))) }
        assertThrows(ForbiddenException::class.java) { modules.pollResults(guest,eventId,poll.id) }
        assertEquals(2,modules.pollResults(owner,eventId,poll.id).totalVotes)
        modules.update(owner,eventId,poll.id,ModuleRecordUpdate(payload=mapOf("options" to options,"allowMultiple" to true,"resultsPublished" to true),status="CLOSED"))
        assertEquals(listOf(1,1),modules.pollResults(guest,eventId,poll.id).options.map{it.votes})
        assertThrows(ConflictException::class.java) { modules.action(guest,eventId,poll.id,ModuleActionRequest("VOTE",mapOf("optionId" to "a"))) }
    }

    @Test fun `questions require moderation and votes follow board rules`() {
        val owner=user()
        val guest=user()
        val other=user()
        val eventId=events.create(owner,CreateEventRequest("Questions","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("INT","Interactions","Guest","Questions"))
        events.configureModule(owner,eventId,"INT",ConfigureModuleRequest())
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        listOf(guest,other).forEach{invitations.save(Invitation(eventId=eventId,linkedUserId=it,guestName="Guest",tokenHash=UUID.randomUUID().toString()))}
        val board=modules.create(owner,eventId,"INT","QUESTION_BOARD",ModuleRecordRequest(payload=mapOf("moderationRequired" to true,"allowVotes" to true)))
        val question=modules.create(guest,eventId,"INT","QUESTION",ModuleRecordRequest(title="¿Cuándo?",parentRecordId=board.id))
        assertEquals("PENDING",question.status)
        assertThrows(NotFoundException::class.java){modules.get(other,eventId,question.id)}
        modules.update(owner,eventId,question.id,ModuleRecordUpdate(status="ACTIVE"))
        modules.action(other,eventId,question.id,ModuleActionRequest("VOTE"))
        assertThrows(ConflictException::class.java){modules.action(other,eventId,question.id,ModuleActionRequest("VOTE"))}
    }

    private fun user()=requireNotNull(users.save(User(name="Poll user",email="poll-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
}
