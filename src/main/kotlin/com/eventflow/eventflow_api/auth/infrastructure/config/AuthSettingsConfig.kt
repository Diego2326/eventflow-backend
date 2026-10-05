package com.eventflow.eventflow_api.auth.infrastructure.config

import com.eventflow.eventflow_api.auth.application.AuthSettings

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthSettingsConfig {
    @Bean
    fun authSettings(
        @Value("\${app.frontend-url:http://localhost:8081}") frontendUrl: String,
        @Value("\${app.auth.expose-tokens:false}") exposeTokens: Boolean
    ) = AuthSettings(frontendUrl, exposeTokens)
}
