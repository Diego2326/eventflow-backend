package com.eventflow.eventflow_api.admin

import com.eventflow.eventflow_api.admin.infrastructure.config.ProductionDemoAccountGuard
import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.domain.UserStatus
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional
import java.util.UUID

class ProductionDemoAccountGuardTest {
    private val users = mock(UserRepository::class.java)
    private val organizerId = UUID.fromString("11111111-1111-4111-8111-111111111111")

    @Test fun `active seed account blocks production even after email changes`() {
        `when`(users.findById(organizerId)).thenReturn(Optional.of(User(
            id = organizerId, name = "Demo", email = "renamed@example.com",
            passwordHash = "unused", status = UserStatus.ACTIVE)))
        assertThrows(IllegalStateException::class.java) {
            ProductionDemoAccountGuard(users).afterSingletonsInstantiated()
        }
    }

    @Test fun `demo email with another id and pending deletion blocks production`() {
        `when`(users.findByEmail("demo.organizador@eventflow.gt")).thenReturn(User(
            name = "Demo", email = "demo.organizador@eventflow.gt",
            passwordHash = "unused", status = UserStatus.DELETION_PENDING))
        assertThrows(IllegalStateException::class.java) {
            ProductionDemoAccountGuard(users).afterSingletonsInstantiated()
        }
    }

    @Test fun `disabled seed account allows production without changing data`() {
        `when`(users.findById(organizerId)).thenReturn(Optional.of(User(
            id = organizerId, name = "Demo", email = "demo.organizador@eventflow.gt",
            passwordHash = "unused", status = UserStatus.DISABLED)))
        assertDoesNotThrow { ProductionDemoAccountGuard(users).afterSingletonsInstantiated() }
        verify(users, never()).save(any(User::class.java))
    }
}
