package com.eventflow.eventflow_api.auth.service

import com.eventflow.eventflow_api.auth.dto.RegisterRequest
import com.eventflow.eventflow_api.auth.model.User
import com.eventflow.eventflow_api.auth.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
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
}
