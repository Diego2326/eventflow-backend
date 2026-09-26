package com.eventflow.eventflow_api.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Pattern
import java.time.LocalDate

data class RegisterRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$", message = "Debe incluir mayúscula, minúscula, número y símbolo")
    val password: String,

    val phonePrefixId: Int? = null,

    @field:Size(max = 32)
    val phoneNumber: String? = null,

    @field:Size(max = 32)
    val phone: String? = null,

    @field:Past
    val birthDate: LocalDate? = null,

    @field:Size(min = 2, max = 2)
    val nationalityCountryCode: String? = null
)
