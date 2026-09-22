package com.eventflow.eventflow_api.auth.controller

import com.eventflow.eventflow_api.auth.dto.RegisterRequest
import com.eventflow.eventflow_api.auth.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Registro y autenticación de usuarios")
class AuthController (
    private val authService: AuthService
){
    @PostMapping("/register")
    @Operation(summary = "Registrar usuario", description = "Crea una nueva cuenta de usuario")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Usuario registrado"),
            ApiResponse(responseCode = "400", description = "Datos inválidos"),
            ApiResponse(responseCode = "409", description = "El correo ya está registrado")
        ]
    )
    fun register(
        @Valid @RequestBody request: RegisterRequest
    ): ResponseEntity<Void> {
        authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}
