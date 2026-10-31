package com.eventflow.eventflow_api.shared.infrastructure.config

import com.eventflow.eventflow_api.auth.infrastructure.security.JwtAuthenticationFilter

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import com.eventflow.eventflow_api.shared.infrastructure.web.ApiError
import com.eventflow.eventflow_api.shared.infrastructure.web.ApiErrorBody
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableMethodSecurity
class SecurityConfig(private val jwtFilter: JwtAuthenticationFilter, private val json: ObjectMapper) {
    @Bean
    fun disableJwtServletRegistration(): FilterRegistrationBean<JwtAuthenticationFilter> =
        FilterRegistrationBean(jwtFilter).apply { isEnabled = false }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { request, response, _ -> writeError(response, 401, "UNAUTHORIZED", "Autenticación requerida", request.requestURI) }
                    .accessDeniedHandler { request, response, _ -> writeError(response, 403, "FORBIDDEN", "No tienes permiso para esta operación", request.requestURI) }
            }
            .authorizeHttpRequests {
                it.requestMatchers("/ws", "/ws/**", "/swagger-ui", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/google", "/api/auth/confirm-email-change").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/invitations/access/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/public/events", "/api/public/events/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/invitations/access/*/rsvp").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/invitations/access/*/assistance").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    private fun writeError(response: jakarta.servlet.http.HttpServletResponse, status: Int, code: String, message: String, path: String) {
        response.status = status
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        json.writeValue(response.outputStream, ApiErrorBody(ApiError(code, message, timestamp = Instant.now(), path = path)))
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = false
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}
