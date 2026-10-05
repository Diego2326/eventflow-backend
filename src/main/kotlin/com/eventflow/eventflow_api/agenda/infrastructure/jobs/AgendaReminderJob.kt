package com.eventflow.eventflow_api.agenda.infrastructure.jobs

import com.eventflow.eventflow_api.agenda.application.AgendaReminderService

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class AgendaReminderJob(private val reminders: AgendaReminderService) {
    @Scheduled(fixedDelay = 60_000)
    fun sendUpcomingReminders() {
        reminders.generate()
    }
}
