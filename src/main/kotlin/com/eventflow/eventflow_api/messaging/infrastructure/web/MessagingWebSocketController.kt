package com.eventflow.eventflow_api.messaging.infrastructure.web

import com.eventflow.eventflow_api.messaging.application.MessageRequest
import com.eventflow.eventflow_api.messaging.application.MessagingService
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.security.Principal
import java.util.UUID

@Controller
class MessagingWebSocketController(private val messaging: MessagingService, private val broker: SimpMessagingTemplate) {
    @MessageMapping("/events/{eventId}/messages")
    fun send(@DestinationVariable eventId: UUID, request: MessageRequest, principal: Principal) {
        val senderId = UUID.fromString(principal.name)
        val saved = messaging.message(senderId, eventId, request)
        val destination = "/queue/events/$eventId/messages"
        broker.convertAndSendToUser(senderId.toString(), destination, saved)
        saved.recipientUserId?.takeIf { it != senderId }?.let { recipient ->
            broker.convertAndSendToUser(recipient.toString(), destination, saved)
        }
    }
}
