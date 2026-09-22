package com.eventflow.eventflow_api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun eventFlowOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("EventFlow API")
                .description("API para la gestión de eventos y usuarios de EventFlow")
                .version("v1")
        )
}
