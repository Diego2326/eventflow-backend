package com.eventflow.eventflow_api.auth.application.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank
    val identifier: String,

    @field:NotBlank
    val password: String
)
