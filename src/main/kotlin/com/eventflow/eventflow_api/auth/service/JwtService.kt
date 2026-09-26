package com.eventflow.eventflow_api.auth.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import com.eventflow.eventflow_api.auth.model.User
import java.util.Date
import java.util.UUID

@Service
class JwtService (
    @Value("\${jwt.secret}")
    private val jwtSecret: String
) {
    private fun getSigningKey() =
            Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtSecret)
            )
    fun generateToken(user: User, sessionId: UUID): String {
        val now = Date()

        val expiration = Date(
            now.time + 60 * 60 * 1000
        )
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("name", user.name)
            .claim("sid", sessionId.toString())
            .claim("roles", user.roles.map { it.name })
            .issuedAt(now)
            .expiration(expiration)
            .signWith(getSigningKey())
            .compact()
    }
    fun parse(token: String) = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).payload
}
