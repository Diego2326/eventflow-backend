package com.eventflow.eventflow_api.infrastructure.identity

import com.eventflow.eventflow_api.application.auth.PasswordHasher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class SpringPasswordHasher(private val encoder: PasswordEncoder) : PasswordHasher {
    override fun encode(raw: String): String = requireNotNull(encoder.encode(raw))
    override fun matches(raw: String, encoded: String): Boolean = encoder.matches(raw, encoded)
}
