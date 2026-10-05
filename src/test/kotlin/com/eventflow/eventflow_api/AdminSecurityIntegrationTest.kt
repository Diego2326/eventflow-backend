package com.eventflow.eventflow_api

import com.eventflow.eventflow_api.application.admin.AdminDecision
import com.eventflow.eventflow_api.application.admin.AdminService
import com.eventflow.eventflow_api.application.admin.UserStatusRequest
import com.eventflow.eventflow_api.auth.model.User
import com.eventflow.eventflow_api.auth.model.UserStatus
import com.eventflow.eventflow_api.common.BadRequestException
import com.eventflow.eventflow_api.common.ConflictException
import com.eventflow.eventflow_api.domain.RoleRequest
import com.eventflow.eventflow_api.infrastructure.persistence.RoleRequestRepository
import com.eventflow.eventflow_api.infrastructure.persistence.UserRepository
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
