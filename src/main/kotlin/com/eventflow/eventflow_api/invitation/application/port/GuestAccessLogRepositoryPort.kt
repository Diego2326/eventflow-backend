package com.eventflow.eventflow_api.invitation.application.port

import com.eventflow.eventflow_api.invitation.domain.GuestAccessLog
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface GuestAccessLogRepositoryPort : CrudPort<GuestAccessLog, UUID> {
    fun findAllByInvitationIdOrderByCreatedAt(invitationId: UUID): List<GuestAccessLog>
    fun findAllByInvitationIdIn(invitationIds: Collection<UUID>): List<GuestAccessLog>
}
