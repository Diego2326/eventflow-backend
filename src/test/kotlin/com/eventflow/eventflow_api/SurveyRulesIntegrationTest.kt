package com.eventflow.eventflow_api

import com.eventflow.eventflow_api.application.event.ConfigureModuleRequest
import com.eventflow.eventflow_api.application.event.CreateEventRequest
import com.eventflow.eventflow_api.application.event.EventService
import com.eventflow.eventflow_api.application.modules.ModuleDataService
import com.eventflow.eventflow_api.application.modules.ModuleRecordRequest
import com.eventflow.eventflow_api.auth.model.User
import com.eventflow.eventflow_api.common.ConflictException
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.infrastructure.persistence.*
import org.junit.jupiter.api.Assertions.assertThrows
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

    private fun user()=requireNotNull(users.save(User(name="Survey user",email="survey-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
}
