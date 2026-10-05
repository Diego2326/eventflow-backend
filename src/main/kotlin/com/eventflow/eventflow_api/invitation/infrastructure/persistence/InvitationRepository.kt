package com.eventflow.eventflow_api.invitation.infrastructure.persistence

import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.invitation.domain.Invitation

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface InvitationRepository : JpaRepository<Invitation, UUID>, InvitationRepositoryPort {
    override fun findByTokenHash(tokenHash: String): Invitation?
    override fun findAllByEventId(eventId: UUID): List<Invitation>
    override fun findAllByLinkedUserId(linkedUserId: UUID): List<Invitation>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Invitation i where i.id=:id") override fun findLocked(id:UUID):Invitation?
}
