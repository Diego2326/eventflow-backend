package com.eventflow.eventflow_api.notification.application

import com.eventflow.eventflow_api.notification.domain.PushDevice
import com.eventflow.eventflow_api.notification.application.port.PushDevicePort
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class RegisterPushDeviceRequest(val expoPushToken: String, val platform: String)
data class PushDeviceResponse(val id: UUID, val platform: String, val createdAt: Instant, val updatedAt: Instant)

@Service
class PushDeviceService(private val devices: PushDevicePort) {
    private val tokenPattern = Regex("^(Expo|Exponent)PushToken\\[[^\\s\\[\\]]{16,280}]$")

    @Transactional
    fun register(userId: UUID, request: RegisterPushDeviceRequest): PushDeviceResponse {
        val token = request.expoPushToken.trim()
        val platform = request.platform.trim().uppercase()
        require(tokenPattern.matches(token)) { "Token Expo inválido" }
        require(platform in PLATFORMS) { "Plataforma inválida" }
        val now = Instant.now()
        val device = devices.findByExpoPushToken(token)?.also {
            if (it.userId != userId) throw ConflictException("El dispositivo ya está asociado a otra cuenta")
            it.revokedAt = null
            it.updatedAt = now
            it.platform = platform
        } ?: PushDevice(userId = userId, expoPushToken = token, platform = platform, createdAt = now, updatedAt = now)
        return devices.save(device).toResponse()
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID): List<PushDeviceResponse> = devices.findAllByUserIdAndRevokedAtIsNullOrderByUpdatedAtDesc(userId).map { it.toResponse() }

    @Transactional
    fun revoke(userId: UUID, deviceId: UUID) {
        val device = devices.findById(deviceId).orElseThrow { NotFoundException("Dispositivo no encontrado") }
        if (device.userId != userId) throw NotFoundException("Dispositivo no encontrado")
        if (device.revokedAt == null) {
            device.revokedAt = Instant.now()
            device.updatedAt = Instant.now()
        }
    }

    private fun PushDevice.toResponse() = PushDeviceResponse(requireNotNull(id), platform, createdAt, updatedAt)

    companion object { private val PLATFORMS = setOf("ANDROID", "IOS", "WEB") }
}
