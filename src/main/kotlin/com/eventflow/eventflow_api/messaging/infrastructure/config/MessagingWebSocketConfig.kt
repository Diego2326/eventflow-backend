package com.eventflow.eventflow_api.messaging.infrastructure.config

import com.eventflow.eventflow_api.auth.infrastructure.security.JwtSessionAuthenticator
import com.eventflow.eventflow_api.messaging.application.MessagingService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.util.UUID

@Configuration
@EnableWebSocketMessageBroker
class MessagingWebSocketConfig(
    private val jwt: JwtSessionAuthenticator,
    private val messaging: MessagingService,
    @Value("\${app.websocket.allowed-origins:*}") private val configuredOrigins: String
) : WebSocketMessageBrokerConfigurer {
    private val origins = configuredOrigins.split(',').map(String::trim).filter(String::isNotEmpty).toTypedArray()

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(*origins)
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
        registry.enableSimpleBroker("/queue")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(object : ChannelInterceptor {
            override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
                val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
                    ?: throw AccessDeniedException("Invalid WebSocket frame")
                when (accessor.command) {
                    StompCommand.CONNECT, StompCommand.STOMP -> {
                        val header = accessor.getFirstNativeHeader("Authorization")
                            ?: throw AccessDeniedException("Bearer token required")
                        if (!header.startsWith("Bearer ")) throw AccessDeniedException("Bearer token required")
                        accessor.user = jwt.authenticate(header.removePrefix("Bearer "))
                            ?: throw AccessDeniedException("Invalid session")
                    }
                    StompCommand.SUBSCRIBE -> {
                        val authentication = accessor.user as? Authentication ?: throw AccessDeniedException("Authentication required")
                        if (!jwt.isActive(authentication)) throw AccessDeniedException("Session is no longer active")
                        val match = SUBSCRIPTION.matchEntire(accessor.destination ?: "")
                            ?: throw AccessDeniedException("Subscription destination is not allowed")
                        messaging.authorizeSocketAccess(UUID.fromString(authentication.name), UUID.fromString(match.groupValues[1]))
                    }
                    StompCommand.SEND -> {
                        val authentication = accessor.user as? Authentication
                        if (authentication == null || !jwt.isActive(authentication) || SEND_DESTINATION.matchEntire(accessor.destination ?: "") == null)
                            throw AccessDeniedException("Send destination is not allowed")
                    }
                    StompCommand.DISCONNECT, null -> Unit
                    else -> throw AccessDeniedException("STOMP command is not allowed")
                }
                return message
            }
        })
    }

    companion object {
        private val SUBSCRIPTION = Regex("^/user/queue/events/([0-9a-fA-F-]{36})/messages$")
        private val SEND_DESTINATION = Regex("^/app/events/([0-9a-fA-F-]{36})/messages$")
    }
}
