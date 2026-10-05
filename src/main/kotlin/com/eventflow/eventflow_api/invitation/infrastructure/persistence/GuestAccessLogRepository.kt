package com.eventflow.eventflow_api.invitation.infrastructure.persistence

import com.eventflow.eventflow_api.invitation.application.port.GuestAccessLogRepositoryPort
import com.eventflow.eventflow_api.invitation.domain.GuestAccessLog

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GuestAccessLogRepository : JpaRepository<GuestAccessLog, UUID>, GuestAccessLogRepositoryPort {
    override fun findAllByInvitationIdOrderByCreatedAt(invitationId: UUID): List<GuestAccessLog>
    override fun findAllByInvitationIdIn(invitationIds: Collection<UUID>): List<GuestAccessLog>
}
