package com.eventflow.eventflow_api.messaging.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "conversation_message")
class ConversationMessage(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "message_id") var id: UUID? = null,
    @Column(name = "event_id") var eventId: UUID? = null,
    @Column(name = "reservation_id") var reservationId: UUID? = null,
    @Column(name = "sender_user_id", nullable = false) var senderUserId: UUID,
    @Column(name = "recipient_user_id") var recipientUserId: UUID? = null,
    @Column(nullable = false) var channel: String,
    @Column(nullable = false, columnDefinition = "text") var body: String,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
