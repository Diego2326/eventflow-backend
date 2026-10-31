package com.eventflow.eventflow_api.auth.infrastructure.security

import com.eventflow.eventflow_api.auth.domain.UserStatus
import com.eventflow.eventflow_api.auth.infrastructure.identity.JwtService
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserSessionRepository
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class JwtSessionAuthenticator(
    private val jwtService: JwtService,
    private val users: UserRepository,
    private val sessions: UserSessionRepository
) {
    fun authenticate(rawToken: String): Authentication? = runCatching {
        val claims = jwtService.parse(rawToken)
        val userId = UUID.fromString(claims.subject)
        val sessionId = UUID.fromString(claims["sid"].toString())
        val session = sessions.findById(sessionId).orElse(null) ?: return null
        val user = users.findWithRolesById(userId) ?: return null
        if (session.userId != userId || session.revokedAt != null || !session.expiresAt.isAfter(Instant.now()) ||
            user.status !in setOf(UserStatus.ACTIVE, UserStatus.DELETION_PENDING)) return null
        UsernamePasswordAuthenticationToken(userId.toString(), null,
            user.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }).apply { details = sessionId }
    }.getOrNull()

    fun isActive(authentication: Authentication): Boolean = runCatching {
        val userId = UUID.fromString(authentication.name)
        val sessionId = authentication.details as? UUID ?: return false
        val session = sessions.findById(sessionId).orElse(null) ?: return false
        val user = users.findById(userId).orElse(null) ?: return false
        session.userId == userId && session.revokedAt == null && session.expiresAt.isAfter(Instant.now()) &&
            user.status in setOf(UserStatus.ACTIVE, UserStatus.DELETION_PENDING)
    }.getOrDefault(false)
}
