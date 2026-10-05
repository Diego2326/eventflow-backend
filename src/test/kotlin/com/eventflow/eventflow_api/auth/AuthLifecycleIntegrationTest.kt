package com.eventflow.eventflow_api.auth

import com.eventflow.eventflow_api.auth.application.AuthService
import com.eventflow.eventflow_api.auth.application.dto.RegisterRequest
import com.eventflow.eventflow_api.auth.domain.UserStatus
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.shared.application.error.ConflictException

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest
class AuthLifecycleIntegrationTest {
    @Autowired lateinit var auth: AuthService
    @Autowired lateinit var users: UserRepository

    @Test fun `verification and password recovery cannot reactivate deleted account`() {
        val email = "deleted-${UUID.randomUUID()}@example.com"
        val token = requireNotNull(auth.register(RegisterRequest(
            name = "Deleted", email = email, password = "Strong#Pass1", phone = "+502${(10000000..99999999).random()}"
        )))
        val account = requireNotNull(users.findByEmail(email))
        account.status = UserStatus.DELETED
        users.save(account)
        assertThrows(ConflictException::class.java) { auth.verifyEmail(token) }
        assertNull(auth.forgotPassword(email))
        assertEquals(UserStatus.DELETED, users.findByEmail(email)?.status)
    }
}
