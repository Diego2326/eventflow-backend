package com.eventflow.eventflow_api.auth.repository

import com.eventflow.eventflow_api.auth.model.User
import com.eventflow.eventflow_api.auth.model.AuthToken
import com.eventflow.eventflow_api.auth.model.AuthTokenType
import com.eventflow.eventflow_api.auth.model.UserSession
import com.eventflow.eventflow_api.auth.model.NotificationPreference
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): User?
    fun findByPhoneNumber(phoneNumber: String): User?
    fun findByGoogleSubject(googleSubject: String): User?
    @Query(value = "select u.* from users u join phone_prefix p on p.phone_prefix_id=u.user_phone_prefix_id where concat(p.phone_prefix,u.user_phone_number)=:phone limit 1", nativeQuery = true)
    fun findByInternationalPhone(phone: String): User?
}

interface AuthTokenRepository : JpaRepository<AuthToken, UUID> {
    fun findByTokenHashAndType(tokenHash: String, type: AuthTokenType): AuthToken?
    @Modifying @Query("update AuthToken t set t.revokedAt = :now where t.userId = :userId and t.type = :type and t.revokedAt is null")
    fun revokeActive(userId: UUID, type: AuthTokenType, now: Instant): Int
}

interface UserSessionRepository : JpaRepository<UserSession, UUID> {
    fun findByRefreshTokenHash(refreshTokenHash: String): UserSession?
    @Modifying @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    fun revokeAll(userId: UUID, now: Instant): Int
}

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, com.eventflow.eventflow_api.auth.model.NotificationPreferenceId> {
    fun findAllByUserId(userId: UUID): List<NotificationPreference>
}
