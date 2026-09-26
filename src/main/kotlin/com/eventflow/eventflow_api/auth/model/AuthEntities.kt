package com.eventflow.eventflow_api.auth.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AuthTokenType { EMAIL_VERIFICATION, PASSWORD_RESET, EMAIL_CHANGE }

@Entity
@Table(name = "auth_token")
class AuthToken(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "auth_token_id") var id: UUID? = null,
    @Column(name = "user_id", nullable = false) var userId: UUID,
    @Column(name = "token_hash", nullable = false, unique = true) var tokenHash: String,
    @Enumerated(EnumType.STRING) @Column(name = "token_type", nullable = false) var type: AuthTokenType,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant,
    @Column(name = "used_at") var usedAt: Instant? = null,
    @Column(name = "revoked_at") var revokedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "user_session")
class UserSession(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id") var id: UUID? = null,
    @Column(name = "user_id", nullable = false) var userId: UUID,
    @Column(name = "refresh_token_hash", nullable = false, unique = true) var refreshTokenHash: String,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant,
    @Column(name = "revoked_at") var revokedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "last_used_at", nullable = false) var lastUsedAt: Instant = Instant.now(),
    @Column(name = "user_agent") var userAgent: String? = null,
    @Column(name = "ip_address") var ipAddress: String? = null
)

@Entity
@Table(name = "notification_preference")
@IdClass(NotificationPreferenceId::class)
class NotificationPreference(
    @Id @Column(name = "user_id") var userId: UUID = UUID.randomUUID(),
    @Id @Column(name = "channel") var channel: String = "IN_APP",
    @Column(name = "enabled", nullable = false) var enabled: Boolean = true
)

data class NotificationPreferenceId(var userId: UUID? = null, var channel: String? = null) : java.io.Serializable
