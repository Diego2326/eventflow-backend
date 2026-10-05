package com.eventflow.eventflow_api.notification.application.port

import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface NotificationRepositoryPort : CrudPort<NotificationEntity, UUID> {
    fun findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId: UUID): List<NotificationEntity>
    fun existsByDedupeKey(dedupeKey: String): Boolean
}
