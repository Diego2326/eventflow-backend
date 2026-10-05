package com.eventflow.eventflow_api.notification.infrastructure.persistence

import com.eventflow.eventflow_api.notification.application.port.NotificationRepositoryPort
import com.eventflow.eventflow_api.notification.domain.NotificationEntity

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<NotificationEntity, UUID>, NotificationRepositoryPort {
    override fun findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId: UUID): List<NotificationEntity>
    override fun existsByDedupeKey(dedupeKey: String): Boolean
}
