package com.eventflow.eventflow_api

import com.eventflow.eventflow_api.agenda.application.AgendaReminderService
import com.eventflow.eventflow_api.agenda.domain.AgendaItem
import com.eventflow.eventflow_api.agenda.infrastructure.persistence.AgendaItemRepository
import com.eventflow.eventflow_api.assistance.domain.AssistanceStatus
import com.eventflow.eventflow_api.auth.application.AuthService
import com.eventflow.eventflow_api.auth.application.dto.LoginRequest
import com.eventflow.eventflow_api.auth.application.dto.RegisterRequest
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.application.CheckRequest
import com.eventflow.eventflow_api.invitation.application.CreateInvitationRequest
import com.eventflow.eventflow_api.invitation.application.GuestAssistanceRequest
import com.eventflow.eventflow_api.invitation.application.InvitationService
import com.eventflow.eventflow_api.invitation.application.RsvpRequest
import com.eventflow.eventflow_api.invitation.domain.InvitationStatus
import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.domain.ModuleRecord
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleRecordRepository
import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.notification.infrastructure.persistence.NotificationRepository
import com.eventflow.eventflow_api.agenda.application.AgendaService

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
class EventflowApiApplicationTests {
    @Autowired lateinit var authService: AuthService
    @Autowired lateinit var eventService: EventService
    @Autowired lateinit var moduleCatalog: ModuleCatalogRepository
    @Autowired lateinit var invitationService: InvitationService
    @Autowired lateinit var agendaItems: AgendaItemRepository
    @Autowired lateinit var notifications: NotificationRepository
    @Autowired lateinit var mapRecords: ModuleRecordRepository
    @Autowired lateinit var moduleData: ModuleDataService
    @Autowired lateinit var operations: AgendaService
    @Autowired lateinit var reminders: AgendaReminderService

    @Test fun contextLoads() = Unit

    @Test
    fun `registration verification login and templated event work end to end`() {
        val token = requireNotNull(authService.register(RegisterRequest(
            name = "Ana Organizadora", email = "ana@example.com", password = "Strong#Pass1", phone = "+50255550000"
        )))
        authService.verifyEmail(token)
        val session = authService.login(LoginRequest("ana@example.com", "Strong#Pass1"), "test", "127.0.0.1")
        assertTrue(session.accessToken.isNotBlank())
        assertTrue(session.refreshToken.isNotBlank())
        moduleCatalog.save(ModuleCatalog("SES", "Sesiones", "Contenido", "Sesiones y ponentes"))
        val event = eventService.create(session.userId, CreateEventRequest("Congreso", "CONFERENCE", Instant.now().plusSeconds(3600)))
        assertEquals("CONFERENCE", event.type)
        assertTrue(eventService.modules(session.userId, event.id).any { it.code == "SES" })
    }

    @Test
    fun `guest invitation exposes event experience rsvp and assistance`() {
        val verification = requireNotNull(authService.register(RegisterRequest(
            name = "Mario Anfitrión", email = "mario@example.com", password = "Strong#Pass1", phone = "+50255550001"
        )))
        authService.verifyEmail(verification)
        val userId = authService.login(LoginRequest("mario@example.com", "Strong#Pass1"), "test", "127.0.0.1").userId
        listOf("INV", "GST", "CAL", "MAP", "AST", "NOT").forEach { code ->
            moduleCatalog.save(ModuleCatalog(code, code, "Invitados", "Módulo $code"))
        }
        val startsAt = Instant.now().plusSeconds(3600)
        val event = eventService.create(userId, CreateEventRequest("Boda", "CUSTOM", startsAt, location = "Antigua Guatemala"))
        listOf("INV", "GST", "CAL", "MAP", "AST", "NOT").forEachIndexed { index, code ->
            eventService.configureModule(userId, event.id, code, ConfigureModuleRequest(order = index, featured = code == "GST"))
        }
        eventService.transition(userId, event.id, EventStatus.PUBLISHED)
        agendaItems.save(AgendaItem(eventId = event.id, title = "Ceremonia", startsAt = startsAt, endsAt = startsAt.plusSeconds(3600), zone = "Capilla"))
        notifications.save(NotificationEntity(eventId = event.id, authorUserId = userId, title = "Bienvenidos", body = "La ceremonia inicia puntualmente"))
        mapRecords.save(ModuleRecord(eventId = event.id, moduleCode = "MAP", recordType = "ZONE", title = "Capilla", payload = "{\"description\":\"Ceremonia\"}"))

        val created = invitationService.create(userId, event.id, CreateInvitationRequest("Sofía", allowedCapacity = 2, table = "Mesa 8"))
        val token = requireNotNull(created.token)
        val experience = invitationService.experience(token)
        assertEquals("Sofía", experience.invitation.guestName)
        assertEquals("Boda", experience.event.name)
        assertEquals(6, experience.modules.size)
        assertEquals("Ceremonia", experience.agenda.single().title)
        assertEquals("Bienvenidos", experience.notifications.single().title)
        assertEquals("Capilla", experience.mapPoints.single().title)
        mapRecords.save(ModuleRecord(eventId = event.id, moduleCode = "MAP", recordType = "POINT", title = "Oculto", payload = "{\"visible\":false}"))
        mapRecords.save(ModuleRecord(eventId = event.id, moduleCode = "MAP", recordType = "POINT", title = "Mesa 8", payload = "{\"tableLabel\":\"Mesa 8\"}"))
        assertEquals(InvitationStatus.ACCEPTED, invitationService.rsvp(token, RsvpRequest(true, listOf("Carlos"))).status)
        val beforeCheckIn = eventService.dashboard(userId, event.id)
        assertEquals(1, beforeCheckIn.acceptedGuests)
        assertEquals(2, beforeCheckIn.remainingCapacity)
        invitationService.check(userId, event.id, created.id, com.eventflow.eventflow_api.invitation.application.CheckRequest(1), true)
        assertEquals(1, eventService.dashboard(userId, event.id).remainingCapacity)
        assertEquals(AssistanceStatus.RECEIVED, invitationService.assistance(token, GuestAssistanceRequest("UBICACION", "No encuentro mi mesa", "Entrada")).status)

        val guestVerification = requireNotNull(authService.register(RegisterRequest(
            name = "Sofía Invitada", email = "sofia@example.com", password = "Strong#Pass1", phone = "+50255550002"
        )))
        authService.verifyEmail(guestVerification)
        val guestUserId = authService.login(LoginRequest("sofia@example.com", "Strong#Pass1"), "test", "127.0.0.1").userId
        assertEquals(created.id, invitationService.linkAll(guestUserId, listOf(" $token ", token)).single().id)
        assertEquals(event.id, eventService.list(guestUserId).single().id)
        assertEquals(created.id, invitationService.mine(guestUserId).single().invitation.id)
        val ceremony = agendaItems.findAllByEventIdOrderByStartsAt(event.id).single()
        operations.favorite(guestUserId, event.id, requireNotNull(ceremony.id), true)
        assertEquals("Ceremonia", operations.myAgenda(guestUserId, event.id).single().title)
        assertTrue(reminders.generate(startsAt.minusSeconds(600)) >= 1)
        assertEquals(0, reminders.generate(startsAt.minusSeconds(600)))
        assertEquals("Mesa 8", moduleData.myMapLocation(guestUserId, event.id).single().title)
        assertEquals(0, moduleData.mapSearch(guestUserId, event.id, "Oculto").size)
        assertEquals("Mesa 8", moduleData.mapSearch(guestUserId, event.id, "Mesa").single().title)
        assertEquals("Boda", invitationService.linkedExperience(guestUserId, created.id).event.name)
        assertEquals(InvitationStatus.DECLINED, invitationService.linkedRsvp(guestUserId, created.id, RsvpRequest(false)).status)
        assertEquals(1, eventService.dashboard(userId, event.id).declinedGuests)
    }
}
