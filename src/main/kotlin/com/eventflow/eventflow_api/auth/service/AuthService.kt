package com.eventflow.eventflow_api.auth.service

import com.eventflow.eventflow_api.auth.dto.AuthResponse
import com.eventflow.eventflow_api.auth.dto.LoginRequest
import com.eventflow.eventflow_api.auth.dto.RegisterRequest
import com.eventflow.eventflow_api.auth.model.User
import com.eventflow.eventflow_api.auth.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {
    fun register(request: RegisterRequest) {
        val normalizedEmail = request.email.trim().lowercase()

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw RuntimeException("El correo ya fue registrado")
        }

        val passwordHash = requireNotNull(passwordEncoder.encode(request.password)) {
            "No se pudo cifrar la contraseña"
        }

        val user = User(
            name = request.name.trim(),
            email = normalizedEmail,
            phonePrefixId = request.phonePrefixId,
            phoneNumber = request.phoneNumber,
            passwordHash = passwordHash,
            birthDate = request.birthDate,
            nationalityCountryCode = request.nationalityCountryCode
        )

        userRepository.save(user)
    }

    fun login(request: LoginRequest): AuthResponse {

        val email = request.email.trim().lowercase()

        val user = userRepository.findByEmail(email)
            ?: throw RuntimeException("Correo o contraseña incorrectos")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw RuntimeException("Correo o contraseña incorrectos")
        }

        val token = jwtService.generateToken(user)

        return AuthResponse(
            accessToken = token,
            userId = requireNotNull(user.id),
            name = user.name,
            email = user.email
        )
    }
}
