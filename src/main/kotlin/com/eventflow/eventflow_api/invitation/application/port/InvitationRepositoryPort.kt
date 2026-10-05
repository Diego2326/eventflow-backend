package com.eventflow.eventflow_api.invitation.application.port

import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface InvitationRepositoryPort : CrudPort<Invitation, UUID> {
    fun findByTokenHash(tokenHash: String): Invitation?
    fun findAllByEventId(eventId: UUID): List<Invitation>
    fun findAllByLinkedUserId(linkedUserId: UUID): List<Invitation>
    fun findLocked(id: UUID): Invitation?
}
