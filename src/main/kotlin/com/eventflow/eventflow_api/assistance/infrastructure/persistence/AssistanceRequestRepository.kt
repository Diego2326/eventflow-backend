package com.eventflow.eventflow_api.assistance.infrastructure.persistence

import com.eventflow.eventflow_api.assistance.application.port.AssistanceRequestRepositoryPort
import com.eventflow.eventflow_api.assistance.domain.AssistanceRequest

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AssistanceRequestRepository : JpaRepository<AssistanceRequest, UUID>, AssistanceRequestRepositoryPort {
    override fun findAllByEventIdOrderByPriorityDescCreatedAtAsc(eventId: UUID): List<AssistanceRequest>
    override fun findAllByInvitationIdOrderByCreatedAtDesc(invitationId: UUID): List<AssistanceRequest>
}
