package com.eventflow.eventflow_api

import com.eventflow.eventflow_api.auth.dto.LoginRequest
import com.eventflow.eventflow_api.auth.dto.RegisterRequest
import com.eventflow.eventflow_api.auth.service.AuthService
import com.eventflow.eventflow_api.event.CreateEventRequest
import com.eventflow.eventflow_api.event.EventService
import com.eventflow.eventflow_api.domain.ModuleCatalog
import com.eventflow.eventflow_api.domain.ModuleCatalogRepository
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
}
