package com.eventflow.eventflow_api.notification.infrastructure.persistence

import com.eventflow.eventflow_api.notification.application.port.NotificationReadRepositoryPort
import com.eventflow.eventflow_api.notification.domain.NotificationRead
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationReadRepository:JpaRepository<NotificationRead,UUID>,NotificationReadRepositoryPort{
    override fun findAllByUserIdAndNotificationIdIn(userId:UUID,notificationIds:Collection<UUID>):List<NotificationRead>
    override fun findByUserIdAndNotificationId(userId:UUID,notificationId:UUID):NotificationRead?
}
