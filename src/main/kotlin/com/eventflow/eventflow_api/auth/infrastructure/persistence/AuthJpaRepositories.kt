package com.eventflow.eventflow_api.auth.infrastructure.persistence

import com.eventflow.eventflow_api.auth.application.port.AuthTokenRepositoryPort
import com.eventflow.eventflow_api.auth.application.port.NotificationPreferenceRepositoryPort
import com.eventflow.eventflow_api.auth.application.port.UserRepositoryPort
import com.eventflow.eventflow_api.auth.application.port.UserSessionRepositoryPort
import com.eventflow.eventflow_api.auth.domain.AuthToken
import com.eventflow.eventflow_api.auth.domain.AuthTokenType
import com.eventflow.eventflow_api.auth.domain.NotificationPreference
import com.eventflow.eventflow_api.auth.domain.NotificationPreferenceId
import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.domain.UserSession
import com.eventflow.eventflow_api.auth.domain.UserStatus

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID>, UserRepositoryPort {
    override fun existsByEmail(email: String): Boolean
    override fun findByEmail(email: String): User?
    override fun findByPhoneNumber(phoneNumber: String): User?
    override fun findByGoogleSubject(googleSubject: String): User?
    override fun findAllByStatusAndDeletionRequestedAtBefore(status: UserStatus, cutoff: Instant): List<User>
    @Query(value = "select u.* from users u join phone_prefix p on p.phone_prefix_id=u.user_phone_prefix_id where concat(p.phone_prefix,u.user_phone_number)=:phone limit 1", nativeQuery = true)
    override fun findByInternationalPhone(phone: String): User?
}

interface AuthTokenRepository : JpaRepository<AuthToken, UUID>, AuthTokenRepositoryPort {
    override fun findByTokenHashAndType(tokenHash: String, type: AuthTokenType): AuthToken?
    @Modifying @Query("update AuthToken t set t.revokedAt = :now where t.userId = :userId and t.type = :type and t.revokedAt is null")
    override fun revokeActive(userId: UUID, type: AuthTokenType, now: Instant): Int
}

interface UserSessionRepository : JpaRepository<UserSession, UUID>, UserSessionRepositoryPort {
    override fun findByRefreshTokenHash(refreshTokenHash: String): UserSession?
    @Modifying @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    override fun revokeAll(userId: UUID, now: Instant): Int
}

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, com.eventflow.eventflow_api.auth.domain.NotificationPreferenceId>, NotificationPreferenceRepositoryPort {
    override fun findAllByUserId(userId: UUID): List<NotificationPreference>
}
