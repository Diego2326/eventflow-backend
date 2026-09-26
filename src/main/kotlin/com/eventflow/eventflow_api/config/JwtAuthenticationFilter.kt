package com.eventflow.eventflow_api.config

import com.eventflow.eventflow_api.auth.model.UserStatus
import com.eventflow.eventflow_api.auth.repository.UserRepository
import com.eventflow.eventflow_api.auth.repository.UserSessionRepository
import com.eventflow.eventflow_api.auth.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.UUID

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val sessionRepository: UserSessionRepository
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header?.startsWith("Bearer ") == true) {
            try {
                val claims = jwtService.parse(header.substring(7))
                val userId = UUID.fromString(claims.subject)
                val sid = UUID.fromString(claims["sid"].toString())
                val session = sessionRepository.findById(sid).orElse(null)
                val user = userRepository.findById(userId).orElse(null)
                if (session != null && session.userId == userId && session.revokedAt == null && session.expiresAt.isAfter(Instant.now()) && user?.status in setOf(UserStatus.ACTIVE, UserStatus.DELETION_PENDING)) {
                    val authorities = user.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }
                    val auth = UsernamePasswordAuthenticationToken(userId.toString(), null, authorities)
                    auth.details = sid
                    SecurityContextHolder.getContext().authentication = auth
                }
            } catch (_: Exception) { SecurityContextHolder.clearContext() }
        }
        chain.doFilter(request, response)
    }
}
