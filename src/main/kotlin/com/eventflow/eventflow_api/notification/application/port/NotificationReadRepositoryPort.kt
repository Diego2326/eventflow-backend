package com.eventflow.eventflow_api.notification.application.port

import com.eventflow.eventflow_api.notification.domain.NotificationRead
import com.eventflow.eventflow_api.shared.application.port.CrudPort
import java.util.UUID

interface NotificationReadRepositoryPort:CrudPort<NotificationRead,UUID>{
    fun findAllByUserIdAndNotificationIdIn(userId:UUID,notificationIds:Collection<UUID>):List<NotificationRead>
    fun findByUserIdAndNotificationId(userId:UUID,notificationId:UUID):NotificationRead?
}
