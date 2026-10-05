package com.eventflow.eventflow_api.messaging.application.port

import com.eventflow.eventflow_api.messaging.domain.ConversationMessage
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface ConversationMessageRepositoryPort : CrudPort<ConversationMessage, UUID> {
    fun findAllByEventIdAndChannelOrderByCreatedAt(eventId: UUID, channel: String): List<ConversationMessage>
    fun findVisible(eventId: UUID, channel: String, userId: UUID): List<ConversationMessage>
}
