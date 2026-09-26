package com.eventflow.eventflow_api.auth.dto

import java.util.UUID

data class AuthResponse (
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 3600,
    val userId: UUID,
    val name: String,
    val email: String,
    val roles: Set<String>
)
