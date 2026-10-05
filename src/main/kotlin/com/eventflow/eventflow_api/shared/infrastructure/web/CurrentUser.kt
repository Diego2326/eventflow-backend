package com.eventflow.eventflow_api.shared.infrastructure.web

import com.eventflow.eventflow_api.shared.application.error.UnauthorizedException

import org.springframework.security.core.Authentication
import java.util.UUID

fun Authentication.userId(): UUID = try { UUID.fromString(name) } catch (_: Exception) { throw UnauthorizedException("Sesión inválida") }
fun Authentication.sessionId(): UUID? = details as? UUID
