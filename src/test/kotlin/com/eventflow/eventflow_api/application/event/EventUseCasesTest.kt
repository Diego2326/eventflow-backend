package com.eventflow.eventflow_api.application.event

import com.eventflow.eventflow_api.domain.EventStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EventUseCasesTest {
    @Test fun `create normalizes input and selects template modules`() {
        val gateway = RecordingCreateGateway()
        val owner = UUID.randomUUID()
        val id = CreateEvent(gateway).execute(NewEvent(owner, "  Congreso  ", " conference ",
            Instant.parse("2026-11-01T10:00:00Z"), null, "America/Guatemala", null,
            null, null, null, false))
        assertEquals(gateway.id, id)
        assertEquals("Congreso", gateway.event?.name)
        assertEquals("CONFERENCE", gateway.event?.type)
        assertEquals("SES", gateway.modules[4])
    }

    @Test fun `invalid event is rejected before persistence`() {
        val gateway = RecordingCreateGateway()
        val instant = Instant.parse("2026-11-01T10:00:00Z")
        assertThrows(InvalidEvent::class.java) {
            CreateEvent(gateway).execute(NewEvent(UUID.randomUUID(), " ", "CUSTOM", instant,
                null, "UTC", null, null, null, null, false))
        }
        assertThrows(InvalidEvent::class.java) {
            CreateEvent(gateway).execute(NewEvent(UUID.randomUUID(), "Valid", "CUSTOM", instant,
                instant, "UTC", null, null, null, null, false))
        }
        assertEquals(null, gateway.event)
    }

    @Test fun `transition checks lifecycle and required modules before writing`() {
        val gateway = RecordingStatusGateway()
        val useCase = TransitionEvent(gateway)
        val id = UUID.randomUUID()
        assertThrows(EventTransitionFailure.Invalid::class.java) {
            useCase.execute(id, EventStatus.DRAFT, EventStatus.FINISHED)
        }
        assertThrows(EventTransitionFailure.NoEnabledModules::class.java) {
            useCase.execute(id, EventStatus.DRAFT, EventStatus.PUBLISHED)
        }
        assertEquals(null, gateway.saved)
        gateway.hasModule = true
        assertEquals(EventStatus.PUBLISHED, useCase.execute(id, EventStatus.DRAFT, EventStatus.PUBLISHED))
        assertEquals(EventStatus.PUBLISHED, gateway.saved)
    }

    private class RecordingCreateGateway : CreateEventGateway {
        val id = UUID.randomUUID()
        var event: NewEvent? = null
        var modules: List<String> = emptyList()
        override fun save(event: NewEvent, suggestedModules: List<String>): UUID {
            this.event = event
            modules = suggestedModules
            return id
        }
    }

    private class RecordingStatusGateway : EventStatusGateway {
        var hasModule = false
        var saved: EventStatus? = null
        override fun hasEnabledModule(eventId: UUID) = hasModule
        override fun changeStatus(eventId: UUID, status: EventStatus) { saved = status }
    }
}
