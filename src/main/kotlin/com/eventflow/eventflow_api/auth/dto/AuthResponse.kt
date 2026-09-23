package com.eventflow.eventflow_api.auth.dto

import java.util.UUID

data class AuthResponse (
    val accessToken: String,
    val userId: UUID,
    val name: String,
    val email: String
)