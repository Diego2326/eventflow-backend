package com.eventflow.eventflow_api.notification.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name = "notification")
class NotificationEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "notification_id") var id: UUID? = null,
    @Column(name = "event_id") var eventId: UUID? = null,
    @Column(name = "author_user_id") var authorUserId: UUID? = null,
    @Column(name = "recipient_user_id") var recipientUserId: UUID? = null,
    @Column(nullable = false) var title: String,
    @Column(nullable = false, columnDefinition = "text") var body: String,
    @Column(name = "audience_type", nullable = false) var audienceType: String = "ALL",
    @Column(name = "audience_value") var audienceValue: String? = null,
    @Column(nullable = false) var channel: String = "IN_APP",
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "dedupe_key", unique = true) var dedupeKey: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
