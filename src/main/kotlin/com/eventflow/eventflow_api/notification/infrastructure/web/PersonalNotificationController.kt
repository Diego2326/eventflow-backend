package com.eventflow.eventflow_api.notification.infrastructure.web

import com.eventflow.eventflow_api.notification.application.NotificationService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/profile/notifications")
class PersonalNotificationController(private val notifications: NotificationService) {
    @GetMapping fun list(authentication: Authentication) = notifications.personalNotifications(authentication.userId())
    @PostMapping("/{id}/read") fun read(authentication: Authentication, @PathVariable id: UUID) =
        notifications.markPersonalRead(authentication.userId(), id)
}
