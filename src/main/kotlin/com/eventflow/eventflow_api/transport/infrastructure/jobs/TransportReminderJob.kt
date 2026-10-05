package com.eventflow.eventflow_api.transport.infrastructure.jobs

import com.eventflow.eventflow_api.transport.application.TransportReminderService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component class TransportReminderJob(private val reminders:TransportReminderService){
    @Scheduled(fixedDelay=60_000)
    fun sendUpcomingReminders(){reminders.generate()}
}
