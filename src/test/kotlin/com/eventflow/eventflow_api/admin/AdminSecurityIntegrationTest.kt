package com.eventflow.eventflow_api.admin

import com.eventflow.eventflow_api.admin.application.AdminDecision
import com.eventflow.eventflow_api.admin.application.AdminService
import com.eventflow.eventflow_api.admin.application.UserStatusRequest
import com.eventflow.eventflow_api.admin.domain.RoleRequest
import com.eventflow.eventflow_api.admin.infrastructure.persistence.RoleRequestRepository
import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.domain.UserStatus
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest
class AdminSecurityIntegrationTest {
    @Autowired lateinit var admin: AdminService
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var requests: RoleRequestRepository

    @Test fun `admin cannot reactivate deleted accounts or approve roles for inactive accounts`() {
        val adminId = user(UserStatus.ACTIVE)
        val deletedId = user(UserStatus.DELETED)
        val unverifiedId = user(UserStatus.DISABLED)
        assertThrows(BadRequestException::class.java) { admin.userStatus(adminId, deletedId, UserStatusRequest(UserStatus.ACTIVE)) }
        assertThrows(ConflictException::class.java) { admin.userStatus(adminId, adminId, UserStatusRequest(UserStatus.DISABLED)) }
        assertThrows(ConflictException::class.java) { admin.userStatus(adminId, unverifiedId, UserStatusRequest(UserStatus.ACTIVE)) }
        val request = requests.save(RoleRequest(userId = deletedId, requestedRole = "SERVICE_PROVIDER"))
        assertThrows(ConflictException::class.java) { admin.decideRole(adminId, requireNotNull(request.id), AdminDecision(true)) }
    }

    private fun user(status: UserStatus) = requireNotNull(users.save(User(
        name = "Admin test", email = "admin-test-${UUID.randomUUID()}@example.com", passwordHash = "unused", status = status
    )).id)
}
