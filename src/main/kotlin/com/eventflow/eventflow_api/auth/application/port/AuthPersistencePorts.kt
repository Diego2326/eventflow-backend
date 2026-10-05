package com.eventflow.eventflow_api.auth.application.port

import com.eventflow.eventflow_api.auth.domain.AuthToken
import com.eventflow.eventflow_api.auth.domain.AuthTokenType
import com.eventflow.eventflow_api.auth.domain.NotificationPreference
import com.eventflow.eventflow_api.auth.domain.NotificationPreferenceId
import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.domain.UserSession
import com.eventflow.eventflow_api.auth.domain.UserStatus
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.time.Instant
import java.util.UUID

interface UserRepositoryPort : CrudPort<User, UUID> {
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): User?
    fun findByPhoneNumber(phoneNumber: String): User?
    fun findByGoogleSubject(googleSubject: String): User?
    fun findByInternationalPhone(phone: String): User?
    fun findAllByStatusAndDeletionRequestedAtBefore(status: UserStatus, cutoff: Instant): List<User>
}
interface AuthTokenRepositoryPort : CrudPort<AuthToken, UUID> {
    fun findByTokenHashAndType(tokenHash: String, type: AuthTokenType): AuthToken?
    fun revokeActive(userId: UUID, type: AuthTokenType, now: Instant): Int
}
interface UserSessionRepositoryPort : CrudPort<UserSession, UUID> {
    fun findByRefreshTokenHash(refreshTokenHash: String): UserSession?
    fun revokeAll(userId: UUID, now: Instant): Int
}
interface NotificationPreferenceRepositoryPort : CrudPort<NotificationPreference, NotificationPreferenceId> {
    fun findAllByUserId(userId: UUID): List<NotificationPreference>
}
