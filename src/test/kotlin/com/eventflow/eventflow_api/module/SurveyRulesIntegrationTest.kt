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
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.shared.application.error.ConflictException

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest
class SurveyRulesIntegrationTest {
    @Autowired lateinit var events: EventService
    @Autowired lateinit var modules: ModuleDataService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var catalog: ModuleCatalogRepository
    @Autowired lateinit var invitations: InvitationRepository

    @Test fun `post event survey accepts one response per guest`() {
        val owner=user()
        val guest=user()
        val eventId=events.create(owner,CreateEventRequest("Feedback","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("REV","Reviews","Guest","Survey"))
        events.configureModule(owner,eventId,"REV",ConfigureModuleRequest())
        invitations.save(Invitation(eventId=eventId,linkedUserId=guest,guestName="Guest",tokenHash=UUID.randomUUID().toString()))
        val survey=modules.create(owner,eventId,"REV","SURVEY",ModuleRecordRequest(title="Feedback"))
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        val response=ModuleRecordRequest(parentRecordId=survey.id,payload=mapOf("rating" to 5))
        assertThrows(ConflictException::class.java) { modules.create(guest,eventId,"REV","SURVEY_RESPONSE",response) }
        events.transition(owner,eventId,EventStatus.RUNNING)
        events.transition(owner,eventId,EventStatus.FINISHED)
        modules.create(guest,eventId,"REV","SURVEY_RESPONSE",response)
        assertThrows(ConflictException::class.java) { modules.create(guest,eventId,"REV","SURVEY_RESPONSE",response) }
    }

    @Test fun `anonymous survey hides respondent identity and still prevents duplicates`() {
        val owner=user();val guest=user();val other=user()
        val eventId=events.create(owner,CreateEventRequest("Anonymous feedback","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("REV","Reviews","Guest","Survey"))
        events.configureModule(owner,eventId,"REV",ConfigureModuleRequest())
        listOf(guest,other).forEach{invitations.save(Invitation(eventId=eventId,linkedUserId=it,guestName="Guest",tokenHash=UUID.randomUUID().toString()))}
        val survey=modules.create(owner,eventId,"REV","SURVEY",ModuleRecordRequest(title="Anonymous",payload=mapOf("anonymous" to true)))
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        events.transition(owner,eventId,EventStatus.RUNNING)
        events.transition(owner,eventId,EventStatus.FINISHED)
        val response=ModuleRecordRequest(parentRecordId=survey.id,payload=mapOf("rating" to 4))
        assertNull(modules.create(guest,eventId,"REV","SURVEY_RESPONSE",response).ownerUserId)
        assertThrows(ConflictException::class.java){modules.create(guest,eventId,"REV","SURVEY_RESPONSE",response)}
        modules.create(other,eventId,"REV","SURVEY_RESPONSE",response)
        assertEquals(2,modules.list(owner,eventId,"REV","SURVEY_RESPONSE").size)
    }

    private fun user()=requireNotNull(users.save(User(name="Survey user",email="survey-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
}
