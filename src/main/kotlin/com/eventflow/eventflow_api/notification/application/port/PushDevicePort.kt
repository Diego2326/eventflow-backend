package com.eventflow.eventflow_api.notification.application.port

import com.eventflow.eventflow_api.notification.domain.PushDevice
import java.util.UUID

interface PushDevicePort {
    fun findByExpoPushToken(expoPushToken: String): PushDevice?
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: UUID): List<PushDevice>
    fun findAllByUserIdAndRevokedAtIsNullOrderByUpdatedAtDesc(userId: UUID): List<PushDevice>
    fun findAllByUserIdInAndRevokedAtIsNullOrderByUpdatedAtDesc(userIds: Collection<UUID>): List<PushDevice>
    fun findById(id: UUID): java.util.Optional<PushDevice>
    fun save(device: PushDevice): PushDevice
}
