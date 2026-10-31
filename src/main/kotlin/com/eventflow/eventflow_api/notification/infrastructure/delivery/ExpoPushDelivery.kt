package com.eventflow.eventflow_api.notification.infrastructure.delivery

import com.eventflow.eventflow_api.notification.application.EventPushRequested
import com.eventflow.eventflow_api.notification.application.port.PushDevicePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.client.RestClient

internal data class ExpoPushMessage(val to: String, val title: String, val body: String, val sound: String = "default", val priority: String = "high", val data: Map<String, String>)

@Component
class ExpoPushGateway(
    @Value("\${app.push.expo-endpoint:https://exp.host/--/api/v2/push/send}") private val endpoint: String,
    @Value("\${app.push.expo-access-token:}") private val accessToken: String
) {
    private val client = RestClient.create()
    val configured: Boolean get() = accessToken.isNotBlank()

    internal fun send(messages: List<ExpoPushMessage>): Boolean {
        if (!configured || messages.isEmpty()) return false
        return runCatching {
            client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .body(messages).retrieve().toBodilessEntity()
            true
        }.getOrDefault(false)
    }
}

@Service
class ExpoPushDeliveryListener(private val devices: PushDevicePort, private val gateway: ExpoPushGateway) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun deliver(event: EventPushRequested) {
        if (!gateway.configured || event.userIds.isEmpty()) return
        val deviceTokens = devices.findAllByUserIdInAndRevokedAtIsNullOrderByUpdatedAtDesc(event.userIds)
            .map { it.expoPushToken }.distinct()
        deviceTokens.chunked(100).forEach { tokens ->
            gateway.send(tokens.map { token -> ExpoPushMessage(token, event.title, event.body,
                data = mapOf("eventId" to event.eventId.toString(), "notificationId" to event.notificationId.toString())) })
        }
    }
}
