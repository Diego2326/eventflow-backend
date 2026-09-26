package com.eventflow.eventflow_api.common

import org.springframework.security.core.Authentication
import java.util.UUID

fun Authentication.userId(): UUID = try { UUID.fromString(name) } catch (_: Exception) { throw UnauthorizedException("Sesión inválida") }
fun Authentication.sessionId(): UUID? = details as? UUID
