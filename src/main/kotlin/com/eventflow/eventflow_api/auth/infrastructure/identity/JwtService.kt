package com.eventflow.eventflow_api.auth.infrastructure.identity

import com.eventflow.eventflow_api.auth.application.AccessTokenIssuer

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID

@Service
class JwtService (
    @Value("\${jwt.secret}")
    private val jwtSecret: String
) : AccessTokenIssuer {
    private fun getSigningKey() =
            Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtSecret)
            )
    override fun issue(userId: UUID, email: String, name: String, roles: Set<String>, sessionId: UUID): String {
        val now = Date()

        val expiration = Date(
            now.time + 60 * 60 * 1000
        )
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("name", name)
            .claim("sid", sessionId.toString())
            .claim("roles", roles)
            .issuedAt(now)
            .expiration(expiration)
            .signWith(getSigningKey())
            .compact()
    }
    fun parse(token: String) = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).payload
}
