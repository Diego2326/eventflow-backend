package com.eventflow.eventflow_api.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "push_device")
class PushDevice(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "push_device_id") var id: UUID? = null,
    @Column(name = "user_id", nullable = false) var userId: UUID,
    @Column(name = "expo_push_token", nullable = false, unique = true, length = 300) var expoPushToken: String,
    @Column(nullable = false, length = 8) var platform: String,
    @Column(name = "created_at", nullable = false, updatable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Column(name = "revoked_at") var revokedAt: Instant? = null
)
