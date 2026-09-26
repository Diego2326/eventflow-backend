package com.eventflow.eventflow_api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
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
        .components(Components().addSecuritySchemes("bearerAuth", SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
}
