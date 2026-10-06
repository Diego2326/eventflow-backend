package com.eventflow.eventflow_api.gamification

import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.gamification.application.GamificationService
import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.invitation.domain.InvitationStatus
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

@SpringBootTest class GamificationServiceIntegrationTest {
    @Autowired lateinit var events:EventService
    @Autowired lateinit var modules:ModuleDataService
    @Autowired lateinit var gamification:GamificationService
    @Autowired lateinit var users:UserRepository
    @Autowired lateinit var invitations:InvitationRepository
    @Autowired lateinit var catalog:ModuleCatalogRepository

    @Test fun `staff verified milestone completes mission and awards badge once`() {
        val owner=user();val guest=user()
        val eventId=events.create(owner,CreateEventRequest("Game","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("GAM","Gamification","Guest","Missions"))
        events.configureModule(owner,eventId,"GAM",ConfigureModuleRequest())
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        val invitation=invitations.save(Invitation(eventId=eventId,linkedUserId=guest,guestName="Guest",status=InvitationStatus.ACCEPTED,tokenHash=UUID.randomUUID().toString()))
        val milestone=modules.create(owner,eventId,"GAM","MILESTONE",ModuleRecordRequest(title="Visit stand"))
        val mission=modules.create(owner,eventId,"GAM","MISSION",ModuleRecordRequest(title="Explorer",payload=mapOf(
            "requirements" to listOf(mapOf("recordId" to milestone.id.toString(),"action" to "CHECK_IN")))))
        assertThrows(com.eventflow.eventflow_api.shared.application.error.ConflictException::class.java){modules.archive(owner,eventId,milestone.id)}
        val badge=modules.create(owner,eventId,"GAM","BADGE",ModuleRecordRequest(title="Explorer badge",payload=mapOf("missionId" to mission.id.toString())))
        assertEquals(false,gamification.progress(guest,eventId).missions.single().finished)
        gamification.completeMilestone(owner,eventId,milestone.id,requireNotNull(invitation.id))
        assertThrows(ConflictException::class.java){gamification.completeMilestone(owner,eventId,milestone.id,requireNotNull(invitation.id))}
        assertEquals(true,gamification.progress(guest,eventId).missions.single().finished)
        assertEquals(listOf(badge.id),gamification.progress(guest,eventId).badgeIds)
        assertEquals(listOf(badge.id),gamification.syncBadges(guest,eventId).badgeIds)
        assertEquals(listOf(badge.id),gamification.syncBadges(guest,eventId).badgeIds)
    }
    private fun user()=requireNotNull(users.save(User(name="Game user",email="game-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
}
