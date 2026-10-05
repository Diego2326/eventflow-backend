package com.eventflow.eventflow_api.notification.infrastructure.web

import com.eventflow.eventflow_api.notification.application.NotificationRequest
import com.eventflow.eventflow_api.notification.application.NotificationService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}") class NotificationController(private val s:NotificationService){
    @PostMapping("/notifications") @ResponseStatus(HttpStatus.CREATED) fun notify(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:NotificationRequest)=s.notify(a.userId(),eventId,r)
    @GetMapping("/notifications") fun notifications(a:Authentication,@PathVariable eventId:UUID)=s.notifications(a.userId(),eventId)
}
