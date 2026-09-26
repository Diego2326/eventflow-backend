package com.eventflow.eventflow_api

import com.eventflow.eventflow_api.auth.dto.LoginRequest
import com.eventflow.eventflow_api.auth.dto.RegisterRequest
import com.eventflow.eventflow_api.auth.service.AuthService
import com.eventflow.eventflow_api.event.CreateEventRequest
import com.eventflow.eventflow_api.event.EventService
import com.eventflow.eventflow_api.event.ConfigureModuleRequest
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.invitation.CreateInvitationRequest
import com.eventflow.eventflow_api.invitation.GuestAssistanceRequest
import com.eventflow.eventflow_api.invitation.InvitationService
import com.eventflow.eventflow_api.invitation.RsvpRequest
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
        assertEquals(InvitationStatus.ACCEPTED, invitationService.rsvp(token, RsvpRequest(true, listOf("Carlos"))).status)
        assertEquals(AssistanceStatus.RECEIVED, invitationService.assistance(token, GuestAssistanceRequest("UBICACION", "No encuentro mi mesa", "Entrada")).status)

        val guestVerification = requireNotNull(authService.register(RegisterRequest(
            name = "Sofía Invitada", email = "sofia@example.com", password = "Strong#Pass1", phone = "+50255550002"
        )))
        authService.verifyEmail(guestVerification)
        val guestUserId = authService.login(LoginRequest("sofia@example.com", "Strong#Pass1"), "test", "127.0.0.1").userId
        assertEquals(created.id, invitationService.linkAll(guestUserId, listOf(" $token ", token)).single().id)
        assertEquals(event.id, eventService.list(guestUserId).single().id)
        assertEquals(created.id, invitationService.mine(guestUserId).single().invitation.id)
        assertEquals("Boda", invitationService.linkedExperience(guestUserId, created.id).event.name)
        assertEquals(InvitationStatus.DECLINED, invitationService.linkedRsvp(guestUserId, created.id, RsvpRequest(false)).status)
    }
}
