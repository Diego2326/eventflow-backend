package com.eventflow.eventflow_api.assistance.application.port

import com.eventflow.eventflow_api.assistance.domain.AssistanceRequest
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface AssistanceRequestRepositoryPort : CrudPort<AssistanceRequest, UUID> {
    fun findAllByEventIdOrderByPriorityDescCreatedAtAsc(eventId: UUID): List<AssistanceRequest>
    fun findAllByInvitationIdOrderByCreatedAtDesc(invitationId: UUID): List<AssistanceRequest>
}
