package com.eventflow.eventflow_api.http

import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserSessionRepository
import com.eventflow.eventflow_api.auth.application.GoogleIdentity
import com.eventflow.eventflow_api.auth.application.GoogleIdentityVerifier
import com.eventflow.eventflow_api.auth.infrastructure.identity.JwtService
import com.eventflow.eventflow_api.auth.infrastructure.security.JwtAuthenticationFilter
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.support.ModuleTestFixtures
import com.eventflow.eventflow_api.ticketing.application.TicketingService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.mockito.Mockito
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.JsonNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "logging.level.org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor=OFF",
    "logging.level.org.springframework.security=OFF",
    "logging.level.org.springframework.security.web.FilterChainProxy=OFF",
    "logging.level.org.hibernate.SQL=OFF"
])
class BackendHttpIntegrationTest {
    @Value("\${local.server.port}") lateinit var port: String
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var sessions: UserSessionRepository
    @Autowired lateinit var jwt: JwtService
    @Autowired lateinit var securityChain: FilterChainProxy
    @Autowired lateinit var jwtFilter: JwtAuthenticationFilter
    @Autowired lateinit var events: EventService
    @Autowired lateinit var modules: ModuleTestFixtures
    @Autowired lateinit var catalog: com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
    @Autowired lateinit var ticketing: TicketingService
    @MockitoBean lateinit var googleVerifier: GoogleIdentityVerifier

    @Test
    fun `HTTP admin collections keep legacy responses and support bounded pagination`() {
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("/api/admin/users?page=0&size=1", HttpMethod.GET).statusCode)
        val registered = registeredUser("page-admin")
        val user = users.findById(UUID.fromString(registered.path("userId").asString())).orElseThrow()
        val ordinaryHeaders = bearer(registered.path("accessToken").asString())
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/admin/users?page=0&size=1", HttpMethod.GET, headers=ordinaryHeaders).statusCode)

        jdbc.update("INSERT INTO user_role(user_id, role) VALUES (?, 'ADMIN') ON CONFLICT DO NOTHING", user.id)
        val adminHeaders = bearer(login("page-admin", user.email).path("accessToken").asString())
        val paged = exchange("/api/admin/users?page=0&size=1", HttpMethod.GET, headers=adminHeaders)
        assertEquals(HttpStatus.OK, paged.statusCode, paged.rawBody)
        assertTrue(paged.body!!.isArray,"pagination preserves the legacy JSON array")
        assertEquals(1, paged.body!!.size())
        assertEquals("0",paged.headers["x-page"])
        assertEquals("1",paged.headers["x-page-size"])
        assertTrue(paged.headers["x-total-count"]!!.toLong() >= 1)
        listOf("events","audit","role-requests","reports").forEach { collection ->
            val response=exchange("/api/admin/$collection?page=0&size=2",HttpMethod.GET,headers=adminHeaders)
            assertEquals(HttpStatus.OK,response.statusCode,"$collection: ${response.rawBody}")
            assertTrue(response.body!!.isArray,"$collection must preserve its legacy array")
            assertEquals("0",response.headers["x-page"])
            assertEquals("2",response.headers["x-page-size"])
        }
        assertEquals(HttpStatus.BAD_REQUEST, exchange("/api/admin/users?page=-1&size=1", HttpMethod.GET, headers=adminHeaders).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("/api/admin/audit?page=0&size=101", HttpMethod.GET, headers=adminHeaders).statusCode)
        assertTrue(exchange("/api/admin/users", HttpMethod.GET, headers=adminHeaders).body!!.isArray,
            "omitting pagination parameters preserves the legacy array response")
        assertEquals(HttpStatus.NOT_FOUND, exchange("/api/admin/users/${UUID.randomUUID()}/status", HttpMethod.PATCH,
            mapOf("status" to "ACTIVE"), adminHeaders).statusCode)
    }

    @Test
    fun `HTTP auth refresh profile events RSVP agenda capacity QR notifications and OpenAPI`() {
        val anonymousProfile = exchange("/api/profile", HttpMethod.GET)
        assertEquals(HttpStatus.UNAUTHORIZED, anonymousProfile.statusCode)
        assertEquals("UNAUTHORIZED", anonymousProfile.body!!.path("error").path("code").asString())
        val owner = registeredUser("owner")
        val ownerToken = owner.path("accessToken").asString()
        val claims = jwt.parse(ownerToken)
        assertEquals(owner.path("userId").asString(), claims.subject)
        assertNotNull(sessions.findById(UUID.fromString(claims["sid"].toString())).orElse(null))
        assertTrue(securityChain.filterChains.flatMap { it.filters }.any { it === jwtFilter })
        val ownerHeaders = bearer(ownerToken)
        val sessionsResponse = exchange("/api/profile/sessions", HttpMethod.GET, headers = ownerHeaders)
        assertEquals(HttpStatus.OK, sessionsResponse.statusCode)
        assertTrue(sessionsResponse.body!!.any { it.path("current").asBoolean() })

        val profile = exchange("/api/profile", HttpMethod.GET, headers = ownerHeaders)
        assertEquals(HttpStatus.OK, profile.statusCode, "HTTP ${profile.statusCode} body=${profile.rawBody}")
        assertEquals(owner.path("userId").asString(), profile.body!!.path("id").asString())
        val export = exchange("/api/profile/export", HttpMethod.GET, headers = ownerHeaders)
        assertEquals(HttpStatus.OK, export.statusCode)
        assertEquals(owner.path("userId").asString(), export.body!!.path("profile").path("id").asString())
        assertTrue(export.body!!.path("sessions").any { it.path("current").asBoolean() })

        val refreshed = post("/api/auth/refresh", mapOf("refreshToken" to owner.path("refreshToken").asString()))
        assertEquals(HttpStatus.OK, refreshed.statusCode)
        val rotated = refreshed.body!!.path("refreshToken").asString()
        assertNotEquals(owner.path("refreshToken").asString(), rotated)
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/auth/refresh", mapOf("refreshToken" to owner.path("refreshToken").asString())).statusCode)
        val ownerId = UUID.fromString(owner.path("userId").asString())
        grantOrganizer(ownerId)
        // Issue a fresh JWT after test-only role provisioning so it reflects persisted authorities.
        val ownerLogin = login("owner", owner.path("email").asString())
        val manager = bearer(ownerLogin.path("accessToken").asString())
        val oldSessionId = claims["sid"].toString()
        assertEquals(HttpStatus.NO_CONTENT, exchange("/api/profile/sessions/$oldSessionId", HttpMethod.DELETE, headers = manager).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("/api/profile", HttpMethod.GET, headers = ownerHeaders).statusCode)

        val start = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS)
        val eventResponse = post("/api/events", mapOf("name" to "HTTP Event", "type" to "CUSTOM", "startsAt" to start.toString()), manager)
        assertEquals(HttpStatus.CREATED, eventResponse.statusCode, eventResponse.body.toString())
        val eventId = eventResponse.body!!.path("id").asString()
        val ownerPermissions = exchange("/api/events/$eventId/permissions", HttpMethod.GET, headers = manager)
        assertEquals(HttpStatus.OK, ownerPermissions.statusCode)
        assertTrue(ownerPermissions.body!!.path("owner").asBoolean())
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/admin/events", HttpMethod.GET, headers = manager).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, exchange("/api/admin/audit", HttpMethod.GET, headers = manager).statusCode)

        listOf("INV", "GST", "CAL", "AFO", "NOT", "MSG", "NET").forEachIndexed { index, code ->
            if (!catalog.existsById(code)) modules.register(ModuleCatalog(code, code, "HTTP", "HTTP integration module"))
            modules.configure(ownerId, UUID.fromString(eventId), code, ConfigureModuleRequest(order = index, configuration = mapOf("guestVisible" to true)))
        }
        val published = post("/api/events/$eventId/status/PUBLISHED", emptyMap<String, Any>(), manager)
        assertEquals(HttpStatus.OK, published.statusCode, published.body.toString())
        assertEquals(HttpStatus.OK, exchange("/api/events", HttpMethod.GET, headers = manager).statusCode)
        assertEquals(HttpStatus.OK, exchange("/api/events/$eventId", HttpMethod.GET, headers = manager).statusCode)

        val invitation = post("/api/events/$eventId/invitations", mapOf("guestName" to "Guest", "allowedCapacity" to 1), manager)
        assertEquals(HttpStatus.CREATED, invitation.statusCode, invitation.body.toString())
        val invitationToken = invitation.body!!.path("token").asString()
        val rsvp = post("/api/invitations/access/$invitationToken/rsvp", mapOf("accepted" to true))
        assertEquals(HttpStatus.OK, rsvp.statusCode, rsvp.body.toString())
        val qrPayload = invitation.body!!.path("qrPayload").asString()
        assertTrue(qrPayload.startsWith("eventflow:invite:"))
        val guest = registeredUser("guest")
        val guestHeaders = bearer(guest.path("accessToken").asString())
        val guestId=UUID.fromString(guest.path("userId").asString())
        assertEquals(HttpStatus.OK, post("/api/invitations/access/$invitationToken/link", emptyMap<String, Any>(), guestHeaders).statusCode)
        val anonymousPrivacy=exchange("/api/profile/privacy",HttpMethod.GET)
        assertEquals(HttpStatus.UNAUTHORIZED,anonymousPrivacy.statusCode)
        val defaultPrivacy=exchange("/api/profile/privacy",HttpMethod.GET,headers=guestHeaders)
        assertTrue(defaultPrivacy.body!!.path("networkingProfileVisible").asBoolean())
        val netProfile=post("/api/events/$eventId/module-data/NET/PROFILE",mapOf("title" to "Guest networking","payload" to mapOf("consent" to true,"visible" to true,"displayName" to "Guest networking","interests" to listOf("technology"),"contact" to "guest@example.com")),guestHeaders)
        assertEquals(HttpStatus.CREATED,netProfile.statusCode,netProfile.rawBody)
        val profileId=netProfile.body!!.path("id").asString()
        val privateContact=exchange("/api/profile/privacy",HttpMethod.PUT,mapOf("networkingProfileVisible" to true,"networkingContactVisible" to false),guestHeaders)
        assertEquals(HttpStatus.OK,privateContact.statusCode)
        val redacted=exchange("/api/events/$eventId/networking/profiles/$profileId",HttpMethod.GET,headers=manager)
        assertEquals(HttpStatus.OK,redacted.statusCode)
        assertTrue(redacted.body!!.path("contact").isNull)
        val hidden=exchange("/api/profile/privacy",HttpMethod.PUT,mapOf("networkingProfileVisible" to false,"networkingContactVisible" to false),guestHeaders)
        assertEquals(HttpStatus.OK,hidden.statusCode)
        assertEquals(HttpStatus.NOT_FOUND,exchange("/api/events/$eventId/networking/profiles/$profileId",HttpMethod.GET,headers=manager).statusCode)
        assertEquals(HttpStatus.CONFLICT,exchange("/api/events/$eventId/networking/qr",HttpMethod.GET,headers=guestHeaders).statusCode)

        val sentPrivateMessage=post("/api/events/$eventId/messages",mapOf("channel" to "STAFF","body" to "Private thread message","recipientUserId" to guestId),manager)
        assertEquals(HttpStatus.CREATED,sentPrivateMessage.statusCode,sentPrivateMessage.rawBody)
        val threadSummary=exchange("/api/events/$eventId/messages/conversations?channel=STAFF",HttpMethod.GET,headers=manager).body!!
            .first{it.path("lastMessage").path("body").asString()=="Private thread message"}
        val threadId=threadSummary.path("conversationId").asString()
        assertEquals(HttpStatus.UNAUTHORIZED,exchange("/api/events/$eventId/messages/conversations/$threadId/read-state?channel=STAFF",HttpMethod.GET).statusCode)
        val outsider=registeredUser("chat-outsider")
        assertEquals(HttpStatus.NOT_FOUND,exchange("/api/events/$eventId/messages/conversations/$threadId/read-state?channel=STAFF",HttpMethod.GET,headers=bearer(outsider.path("accessToken").asString())).statusCode)
        assertEquals(HttpStatus.NOT_FOUND,exchange("/api/events/$eventId/messages/conversations/unknown/read-state?channel=STAFF",HttpMethod.GET,headers=guestHeaders).statusCode)
        val marked=post("/api/events/$eventId/messages/conversations/$threadId/read?channel=STAFF",emptyMap<String,Any>(),guestHeaders)
        assertEquals(HttpStatus.OK,marked.statusCode,marked.rawBody)
        assertEquals(1,marked.body!!.path("markedCount").asInt())
        val readState=exchange("/api/events/$eventId/messages/conversations/$threadId/read-state?channel=STAFF",HttpMethod.GET,headers=manager)
        assertTrue(readState.body!!.any{it.path("messageId").asText()==sentPrivateMessage.body!!.path("id").asText()&&it.path("readBy").any{receipt->receipt.path("userId").asText()==guestId.toString()}})
        val forbiddenCheckIn = post("/api/events/$eventId/check-in", mapOf("qrPayload" to qrPayload), guestHeaders)
        val forbiddenCheckOut = post("/api/events/$eventId/check-out", mapOf("qrPayload" to qrPayload), guestHeaders)
        assertEquals(HttpStatus.FORBIDDEN, forbiddenCheckIn.statusCode)
        assertEquals("FORBIDDEN", forbiddenCheckIn.body!!.path("error").path("code").asString())
        assertEquals(HttpStatus.FORBIDDEN, forbiddenCheckOut.statusCode)

        val agenda = post("/api/events/$eventId/agenda", mapOf(
            "title" to "HTTP Session", "startsAt" to start.toString(), "endsAt" to start.plusSeconds(3600).toString()
        ), manager)
        assertEquals(HttpStatus.CREATED, agenda.statusCode, agenda.body.toString())
        val agendaList = exchange("/api/events/$eventId/agenda", HttpMethod.GET, headers = manager)
        assertEquals(1, agendaList.body!!.size())
        assertEquals(HttpStatus.OK, exchange("/api/events/$eventId/agenda/now-next", HttpMethod.GET, headers = manager).statusCode)

        assertEquals(HttpStatus.OK, exchange("/api/events/$eventId/capacity/zones", HttpMethod.GET, headers = manager).statusCode)
        assertEquals(HttpStatus.OK, exchange("/api/events/$eventId/capacity/services", HttpMethod.GET, headers = manager).statusCode)
        assertEquals(HttpStatus.OK, exchange("/api/reservations/mine", HttpMethod.GET, headers = manager).statusCode)

        val pushToken = "ExponentPushToken[12345678901234567890-test-token]"
        val pushRegistration = post("/api/profile/push-devices", mapOf("expoPushToken" to pushToken, "platform" to "ANDROID"), guestHeaders)
        assertEquals(HttpStatus.OK, pushRegistration.statusCode, pushRegistration.rawBody)
        assertFalse(pushRegistration.rawBody.contains(pushToken), "device token must never be returned")
        val notice = post("/api/events/$eventId/notifications", mapOf("title" to "HTTP", "body" to "Notice"), manager)
        assertEquals(HttpStatus.CREATED, notice.statusCode, notice.body.toString())
        val delivered = PushStub.requests.poll(5, TimeUnit.SECONDS) ?: error("Expo notification was not delivered")
        assertEquals("Bearer integration-test-expo-secret", delivered.first)
        assertTrue(delivered.second.contains(pushToken))
        assertTrue(delivered.second.contains(eventId.toString()))
        val notices = exchange("/api/events/$eventId/notifications", HttpMethod.GET, headers = manager)
        assertTrue(notices.body!!.any { it.path("title").asString() == "HTTP" })
        val httpNotice = notices.body!!.first { it.path("title").asString() == "HTTP" }
        val noticeId = httpNotice.path("id").asString()
        assertTrue(httpNotice.path("readAt").isNull)
        val readNotice = post("/api/events/$eventId/notifications/$noticeId/read", emptyMap<String, Any>(), manager)
        assertEquals(HttpStatus.OK, readNotice.statusCode, readNotice.rawBody)
        assertFalse(readNotice.body!!.path("readAt").isNull)
        assertEquals(HttpStatus.OK, exchange("/api/events/$eventId/notifications/$noticeId", HttpMethod.GET, headers = manager).statusCode)
        assertEquals(HttpStatus.OK, post("/api/events/$eventId/notifications/read-all", emptyMap<String, Any>(), manager).statusCode)

        val checkin = post("/api/events/$eventId/check-in", mapOf("qrPayload" to qrPayload), manager)
        assertEquals(HttpStatus.OK, checkin.statusCode, checkin.body.toString())
        assertEquals(1, checkin.body!!.path("checkedIn").asInt())

        val sentMessage = post("/api/events/$eventId/messages", mapOf("channel" to "STAFF", "body" to "HTTP conversation"), manager)
        assertEquals(HttpStatus.CREATED, sentMessage.statusCode, sentMessage.rawBody)
        val conversations = exchange("/api/events/$eventId/messages/conversations?channel=STAFF", HttpMethod.GET, headers = manager)
        assertTrue(conversations.body!!.any { it.path("lastMessage").path("body").asString() == "HTTP conversation" })
        val conversationId = conversations.body!!.first { it.path("lastMessage").path("body").asString() == "HTTP conversation" }.path("conversationId").asString()
        val conversation = exchange("/api/events/$eventId/messages/conversations/$conversationId?channel=STAFF", HttpMethod.GET, headers = manager)
        assertTrue(conversation.body!!.any { it.path("body").asString() == "HTTP conversation" })

        exerciseWebSocket(UUID.fromString(eventId), ownerLogin.path("accessToken").asString())

        val pushDeviceId = pushRegistration.body!!.path("id").asString()
        assertEquals(HttpStatus.OK, exchange("/api/profile/push-devices", HttpMethod.GET, headers = guestHeaders).statusCode)
        val revokedDevice = exchange("/api/profile/push-devices/$pushDeviceId", HttpMethod.DELETE, headers = guestHeaders)
        assertEquals(HttpStatus.NO_CONTENT, revokedDevice.statusCode)

        val docs = exchange("/v3/api-docs", HttpMethod.GET)
        assertEquals(HttpStatus.OK, docs.statusCode)
        assertTrue(docs.rawBody.contains("/api/auth/refresh"))
        val home = exchange("/", HttpMethod.GET)
        assertEquals(HttpStatus.FOUND, home.statusCode)
        assertEquals("/swagger-ui", URI(home.headers["location"]).path)
        assertEquals(HttpStatus.OK, exchange("/swagger-ui/index.html", HttpMethod.GET).statusCode)
    }

    @Test
    fun `HTTP event templates support owned CRUD and dependency-aware application`() {
        assertEquals(HttpStatus.UNAUTHORIZED,exchange("/api/event-templates",HttpMethod.GET).statusCode)
        val nonOrganizer=registeredUser("template-no-role")
        val nonOrganizerHeaders=bearer(nonOrganizer.path("accessToken").asString())
        val request=mapOf("name" to "Small event","eventType" to "CUSTOM","moduleCodes" to listOf("INV"),"description" to "Fixture")
        assertEquals(HttpStatus.FORBIDDEN,post("/api/event-templates",request,nonOrganizerHeaders).statusCode)

        val owner=registeredUser("template-owner")
        val ownerId=UUID.fromString(owner.path("userId").asString())
        grantOrganizer(ownerId)
        val auth=login("template-owner",owner.path("email").asString())
        val headers=bearer(auth.path("accessToken").asString())
        val created=post("/api/event-templates",request,headers)
        assertEquals(HttpStatus.CREATED,created.statusCode,created.rawBody)
        val templateId=created.body!!.path("id").asString()
        assertTrue(created.body!!.path("moduleCodes").any{it.asText()=="INV"})
        val outsider=registeredUser("template-outsider")
        assertEquals(HttpStatus.NOT_FOUND,exchange("/api/event-templates/$templateId",HttpMethod.GET,headers=bearer(outsider.path("accessToken").asString())).statusCode)

        val start=Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS)
        val applied=post("/api/event-templates/apply/$templateId",mapOf("name" to "From template","startsAt" to start.toString()),headers)
        assertEquals(HttpStatus.CREATED,applied.statusCode,applied.rawBody)
        assertTrue(applied.body!!.path("enabledModules").any{it.asText()=="INV"})

        val unavailable=post("/api/event-templates",mapOf("name" to "Unavailable","eventType" to "CUSTOM","moduleCodes" to listOf("ZZZ")),headers)
        assertEquals(HttpStatus.CONFLICT,unavailable.statusCode)
        val updated=exchange("/api/event-templates/$templateId",HttpMethod.PUT,request+mapOf("description" to "Updated"),headers)
        assertEquals(HttpStatus.OK,updated.statusCode,updated.rawBody)
        assertEquals("Updated",updated.body!!.path("description").asText())
        assertEquals(HttpStatus.NO_CONTENT,exchange("/api/event-templates/$templateId",HttpMethod.DELETE,headers=headers).statusCode)
        assertEquals(HttpStatus.NOT_FOUND,exchange("/api/event-templates/$templateId",HttpMethod.GET,headers=headers).statusCode)
        assertTrue(exchange("/api/event-templates",HttpMethod.GET,headers=headers).body!!.any{it.path("id").asText()=="builtin-WEDDING"})
    }

    @Test
    fun `HTTP public ticket purchase returns private QR and check-in accepts it`() {
        val organizer = registeredUser("organizer")
        val organizerId = UUID.fromString(organizer.path("userId").asString())
        grantOrganizer(organizerId)
        val organizerAuth = login("organizer")
        val organizerHeaders = bearer(organizerAuth.path("accessToken").asString())
        val buyer = loginAfterRegistration("buyer")
        val buyerHeaders = bearer(buyer.path("accessToken").asString())

        val start = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS)
        val event = post("/api/events", mapOf("name" to "Ticket HTTP", "type" to "CUSTOM", "startsAt" to start.toString(), "endsAt" to start.plusSeconds(14_400).toString()), organizerHeaders)
        assertEquals(HttpStatus.CREATED, event.statusCode, event.body.toString())
        val eventId = UUID.fromString(event.body!!.path("id").asString())
        if (!catalog.existsById("GST")) modules.register(ModuleCatalog("GST", "Guests", "Access", "Guest access"))
        modules.configure(organizerId, eventId, "GST", ConfigureModuleRequest())
        assertEquals(HttpStatus.OK, post("/api/events/${eventId}/status/PUBLISHED", emptyMap<String, Any>(), organizerHeaders).statusCode)
        assertEquals(HttpStatus.OK, put("/api/events/$eventId/ticket-settings", mapOf("visibility" to "PUBLIC"), organizerHeaders).statusCode)
        val type = post("/api/events/$eventId/ticket-types", mapOf(
            "name" to "General", "price" to "0.00", "currency" to "GTQ", "capacity" to 2, "maxPerOrder" to 2,
            "salesStart" to Instant.now().minusSeconds(30).toString(), "salesEnd" to start.plusSeconds(10_800).toString(), "enabled" to true
        ), organizerHeaders)
        assertEquals(HttpStatus.CREATED, type.statusCode, type.body.toString())
        val publicCatalog = exchange("/api/public/events", HttpMethod.GET)
        assertEquals(HttpStatus.OK, publicCatalog.statusCode)
        assertTrue(publicCatalog.body!!.any { it.path("id").asString() == eventId.toString() })

        val purchase = post("/api/events/$eventId/ticket-orders", mapOf("lines" to listOf(mapOf(
            "ticketTypeId" to type.body!!.path("id").asString(), "quantity" to 1
        ))), buyerHeaders, mapOf("Idempotency-Key" to "http-${UUID.randomUUID()}"))
        assertEquals(HttpStatus.CREATED, purchase.statusCode, purchase.body.toString())
        assertEquals(true, purchase.body!!.path("simulated").asBoolean())
        val qr = purchase.body!!.path("tickets")[0].path("qrPayload").asString()
        assertTrue(qr.startsWith("eventflow:ticket:"))
        val mine = exchange("/api/ticket-orders/mine", HttpMethod.GET, headers = buyerHeaders)
        assertTrue(mine.body!!.any { it.path("id").asString() == purchase.body!!.path("id").asString() })
        val checkin = post("/api/events/$eventId/check-in", mapOf("qrPayload" to qr), organizerHeaders)
        assertEquals(HttpStatus.OK, checkin.statusCode, checkin.body.toString())
        assertEquals("CHECK_IN", checkin.body!!.path("action").asString())
    }

    @Test
    fun `HTTP email reset tokens are single use and email change rotates sessions`() {
        val account = registeredUser("email-flow")
        val email = account.path("email").asString()
        val forgot = post("/api/auth/forgot-password", mapOf("email" to email))
        assertEquals(HttpStatus.OK, forgot.statusCode)
        val resetToken = forgot.body!!.path("developmentToken").asString()
        assertTrue(resetToken.isNotBlank())
        val reset = post("/api/auth/reset-password", mapOf("token" to resetToken, "password" to "Updated#Pass2"))
        assertEquals(HttpStatus.OK, reset.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/auth/reset-password", mapOf("token" to resetToken, "password" to "Updated#Pass2")).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/auth/login", mapOf("identifier" to email, "password" to "Strong#Pass1")).statusCode)
        val login = post("/api/auth/login", mapOf("identifier" to email, "password" to "Updated#Pass2"))
        assertEquals(HttpStatus.OK, login.statusCode)
        val auth = bearer(login.body!!.path("accessToken").asString())
        val changedEmail = "changed-${UUID.randomUUID()}@example.com"
        val requestChange = post("/api/auth/request-email-change", mapOf("email" to changedEmail), auth)
        assertEquals(HttpStatus.OK, requestChange.statusCode)
        val emailToken = requestChange.body!!.path("developmentToken").asString()
        assertTrue(emailToken.isNotBlank())
        assertEquals(HttpStatus.OK, post("/api/auth/confirm-email-change", mapOf("token" to emailToken)).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/auth/login", mapOf("identifier" to email, "password" to "Updated#Pass2")).statusCode)
        assertEquals(HttpStatus.OK, post("/api/auth/login", mapOf("identifier" to changedEmail, "password" to "Updated#Pass2")).statusCode)
    }

    @Test
    fun `HTTP Google sign in links verified pending users and lets new users complete their profile`() {
        Mockito.`when`(googleVerifier.configured).thenReturn(true)
        val newEmail = "google-${UUID.randomUUID()}@example.com"
        Mockito.`when`(googleVerifier.verify("signed-new-id-token")).thenReturn(
            GoogleIdentity("google-subject-new", newEmail, "Google New User", null)
        )
        val created = post("/api/auth/google", mapOf("idToken" to "signed-new-id-token"))
        assertEquals(HttpStatus.OK, created.statusCode, created.rawBody)
        assertFalse(created.body!!.path("profileComplete").asBoolean())
        val createdId = created.body!!.path("userId").asText()
        val profileAuth = bearer(created.body!!.path("accessToken").asText())
        val profile = exchange("/api/profile", HttpMethod.GET, headers = profileAuth)
        assertEquals(createdId, profile.body!!.path("id").asText())
        val completed = exchange("/api/profile", HttpMethod.PATCH,
            mapOf("phoneNumber" to "55551234"), profileAuth)
        assertEquals(HttpStatus.OK, completed.statusCode, completed.rawBody)
        assertTrue(completed.body!!.path("profileComplete").asBoolean())

        Mockito.`when`(googleVerifier.verify("signed-existing-id-token")).thenReturn(
            GoogleIdentity("google-subject-new", newEmail, "Google New User", null)
        )
        val existing = post("/api/auth/google", mapOf("idToken" to "signed-existing-id-token"))
        assertEquals(HttpStatus.OK, existing.statusCode)
        assertEquals(createdId, existing.body!!.path("userId").asText())

        val pendingEmail = "pending-google-${UUID.randomUUID()}@example.com"
        val registration = post("/api/auth/register", mapOf("name" to "Pending", "email" to pendingEmail,
            "password" to "Strong#Pass1", "phone" to "+50255559876"))
        assertEquals(HttpStatus.CREATED, registration.statusCode)
        val pending = users.findByEmail(pendingEmail) ?: error("registered pending account missing")
        Mockito.`when`(googleVerifier.verify("signed-link-id-token")).thenReturn(
            GoogleIdentity("google-subject-link", pendingEmail, "Pending Google", null)
        )
        val linked = post("/api/auth/google", mapOf("idToken" to "signed-link-id-token"))
        assertEquals(HttpStatus.OK, linked.statusCode, linked.rawBody)
        assertEquals(pending.id.toString(), linked.body!!.path("userId").asText())
        assertEquals("ACTIVE", users.findByEmail(pendingEmail)!!.status.name)
    }

    private fun registeredUser(label: String): JsonNode {
        val email = "$label-${UUID.randomUUID()}@example.com"
        val register = post("/api/auth/register", mapOf("name" to label, "email" to email,
            "password" to "Strong#Pass1", "phone" to "+502${(10000000..99999999).random()}"))
        assertEquals(HttpStatus.CREATED, register.statusCode, register.body.toString())
        val verification = register.body!!.path("developmentToken").asString()
        assertTrue(verification.isNotBlank(), "test profile should expose its email verification token")
        val verified = post("/api/auth/verify-email", mapOf("token" to verification))
        assertEquals(HttpStatus.OK, verified.statusCode, verified.body.toString())
        return login(label, email)
    }

    private fun login(label: String, email: String? = null): JsonNode {
        val identifier = email ?: users.findAll().firstOrNull { it.name == label }?.email
            ?: error("No registered user for HTTP fixture")
        val result = post("/api/auth/login", mapOf("identifier" to identifier, "password" to "Strong#Pass1"))
        assertEquals(HttpStatus.OK, result.statusCode, result.body.toString())
        return result.body!!
    }

    private fun loginAfterRegistration(label: String): JsonNode = registeredUser(label)

    private fun grantOrganizer(userId: UUID) {
        jdbc.update("INSERT INTO user_role(user_id, role) VALUES (?, 'ORGANIZER') ON CONFLICT DO NOTHING", userId)
    }

    private fun bearer(token: String) = mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json")

    private fun post(path: String, body: Any, headers: Map<String, String> = emptyMap(), extra: Map<String, String> = emptyMap()) =
        exchange(path, HttpMethod.POST, body, headers + extra)

    private fun put(path: String, body: Any, headers: Map<String, String>) =
        exchange(path, HttpMethod.PUT, body, headers)

    private fun exchange(path: String, method: HttpMethod, body: Any? = null, headers: Map<String, String> = emptyMap()): HttpResult {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).apply {
            headers.forEach { (name, value) -> header(name, value) }
            if (body != null && "Content-Type" !in headers) header("Content-Type", "application/json")
            if (body == null) method(method.name(), HttpRequest.BodyPublishers.noBody())
            else method(method.name(), HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
        }.build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        val contentType = response.headers().firstValue("content-type").orElse("")
        val parsed = response.body().takeIf { it.isNotBlank() && contentType.contains("json", ignoreCase = true) }?.let(json::readTree)
        val responseHeaders=response.headers().map().mapNotNull{(key,values)->values.firstOrNull()?.let{key.lowercase() to it}}.toMap()
        return HttpResult(HttpStatus.valueOf(response.statusCode()), parsed, response.body(),responseHeaders)
    }

    private fun exerciseWebSocket(eventId: UUID, accessToken: String) {
        val listener = StompFrames()
        val socket = HttpClient.newHttpClient().newWebSocketBuilder()
            .buildAsync(URI("ws://127.0.0.1:$port/ws"), listener).join()
        try {
            socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer $accessToken\n\n\u0000", true).join()
            assertTrue(listener.next().contains("CONNECTED"), "STOMP CONNECT must authenticate the JWT")
            socket.sendText("SUBSCRIBE\nid:private-1\ndestination:/user/queue/events/$eventId/messages\nack:auto\n\n\u0000", true).join()
            val body = json.writeValueAsString(mapOf("channel" to "STAFF", "body" to "websocket-http-message"))
            socket.sendText("SEND\ndestination:/app/events/$eventId/messages\ncontent-type:application/json\n\n$body\u0000", true).join()
            val message = listener.next()
            assertTrue(message.contains("MESSAGE"), "authorized sender should receive a private queue message")
            assertTrue(message.contains("websocket-http-message"))

            val sessionId = UUID.fromString(jwt.parse(accessToken)["sid"].toString())
            val session = sessions.findById(sessionId).orElseThrow()
            session.revokedAt = Instant.now()
            sessions.save(session)
            socket.sendText("SEND\ndestination:/app/events/$eventId/messages\ncontent-type:application/json\n\n$body\u0000", true).join()
            assertTrue(listener.next().contains("ERROR"), "revoked sessions cannot keep sending over an open socket")
        } finally {
            runCatching { socket.sendClose(WebSocket.NORMAL_CLOSURE, "test-complete").join() }
        }
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun stopExpoStub() = PushStub.stop()

        @JvmStatic
        @DynamicPropertySource
        fun expoProperties(registry: DynamicPropertyRegistry) {
            val endpoint = PushStub.start()
            registry.add("app.push.expo-endpoint") { endpoint }
            registry.add("app.push.expo-access-token") { "integration-test-expo-secret" }
        }
    }
}

private data class HttpResult(val statusCode: HttpStatus, val body: JsonNode?, val rawBody: String,val headers:Map<String,String> = emptyMap())

private class StompFrames : WebSocket.Listener {
    private val frames = LinkedBlockingQueue<String>()
    override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*>? {
        frames.offer(data.toString())
        webSocket.request(1)
        return CompletableFuture.completedFuture(null)
    }
    fun next(): String = frames.poll(5, TimeUnit.SECONDS) ?: error("Timed out waiting for STOMP frame")
}

private object PushStub {
    val requests = LinkedBlockingQueue<Pair<String, String>>()
    @Volatile private var server: HttpServer? = null

    @Synchronized fun start(): String {
        val current = server ?: HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { http ->
            http.createContext("/send") { exchange ->
                val body = exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
                requests.offer(exchange.requestHeaders.getFirst("Authorization") to body)
                val response = "{\"data\":[{\"status\":\"ok\",\"id\":\"local-stub\"}]}".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            http.start()
        }.also { server = it }
        return "http://127.0.0.1:${current.address.port}/send"
    }

    @Synchronized fun stop() {
        server?.stop(0)
        server = null
    }
}
