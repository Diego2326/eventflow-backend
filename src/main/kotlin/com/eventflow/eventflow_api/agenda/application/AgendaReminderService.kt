package com.eventflow.eventflow_api.agenda.application

import com.eventflow.eventflow_api.agenda.application.port.AgendaFavoriteRepositoryPort
import com.eventflow.eventflow_api.agenda.application.port.AgendaItemRepositoryPort
import com.eventflow.eventflow_api.auth.application.port.NotificationPreferenceRepositoryPort
import com.eventflow.eventflow_api.auth.domain.NotificationPreferenceId
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.notification.application.port.NotificationRepositoryPort
import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Service
class AgendaReminderService(
    private val agenda: AgendaItemRepositoryPort,
    private val favorites: AgendaFavoriteRepositoryPort,
    private val notifications: NotificationRepositoryPort,
    private val preferences: NotificationPreferenceRepositoryPort,
    private val events: EventService
) {
    @Transactional
    fun generate(now: Instant = Instant.now()): Int {
        val upcoming = agenda.findAllByStartsAtBetweenAndStatusNot(now, now.plus(Duration.ofMinutes(15)), "CANCELLED")
        if (upcoming.isEmpty()) return 0
        val byId = upcoming.associateBy { requireNotNull(it.id) }
        var created = 0
        favorites.findAllByAgendaItemIdIn(byId.keys).forEach { favorite ->
            val activity = byId[favorite.agendaItemId] ?: return@forEach
            val key = "AGENDA:${favorite.agendaItemId}:${favorite.userId}"
            if (notifications.existsByDedupeKey(key)) return@forEach
            if (preferences.findById(NotificationPreferenceId(favorite.userId, "IN_APP")).map { !it.enabled }.orElse(false)) return@forEach
            val event = try {
                events.requireVisibleModule(favorite.userId, activity.eventId, "CAL")
                events.requireVisibleModule(favorite.userId, activity.eventId, "NOT")
                events.accessible(favorite.userId, activity.eventId)
            } catch (_: ForbiddenException) { return@forEach }
              catch (_: ConflictException) { return@forEach }
            if (event.status !in setOf(EventStatus.PUBLISHED, EventStatus.RUNNING)) return@forEach
            val time = activity.startsAt.atZone(ZoneId.of(event.timezone)).toLocalTime()
            notifications.save(NotificationEntity(eventId = activity.eventId, recipientUserId = favorite.userId,
                title = "Actividad próxima", body = "${activity.title} comienza a las $time",
                audienceType = "USER", dedupeKey = key))
            created++
        }
        return created
    }
}
