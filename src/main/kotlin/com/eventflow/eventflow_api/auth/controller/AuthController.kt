package com.eventflow.eventflow_api.auth.controller

import com.eventflow.eventflow_api.auth.dto.*
import com.eventflow.eventflow_api.auth.service.*
import com.eventflow.eventflow_api.common.sessionId
import com.eventflow.eventflow_api.common.userId
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class MessageResponse(val message: String, val developmentToken: String? = null)

@RestController @RequestMapping("/api/auth")
@Tag(name = "Autenticación")
class AuthController(private val authService: AuthService) {
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): MessageResponse =
        MessageResponse("Cuenta creada. Revisa el enlace de activación.", authService.register(request))

    @PostMapping("/verify-email") fun verify(@RequestBody request: TokenRequest): MessageResponse {
        authService.verifyEmail(request.token); return MessageResponse("Correo verificado")
    }
    @PostMapping("/resend-verification") fun resend(@RequestBody request: ForgotPasswordRequest) =
        MessageResponse("Si la cuenta está pendiente, se envió un nuevo enlace.", authService.resendVerification(request.email))

    @PostMapping("/login") fun login(@Valid @RequestBody request: LoginRequest, http: HttpServletRequest): AuthResponse =
        authService.login(request, http.getHeader("User-Agent"), http.remoteAddr)
    @PostMapping("/google") fun google(@RequestBody request: GoogleLoginRequest, http: HttpServletRequest): AuthResponse =
        authService.googleLogin(request, http.getHeader("User-Agent"), http.remoteAddr)
    @PostMapping("/refresh") fun refresh(@RequestBody request: RefreshRequest) = authService.refresh(request.refreshToken)
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(authentication: Authentication) = authService.logout(authentication.userId(), authentication.sessionId(), false)
    @PostMapping("/logout-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logoutAll(authentication: Authentication) = authService.logout(authentication.userId(), null, true)

    @PostMapping("/forgot-password") fun forgot(@RequestBody request: ForgotPasswordRequest) =
        MessageResponse("Si el correo existe, se envió un enlace temporal.", authService.forgotPassword(request.email))
    @PostMapping("/reset-password") fun reset(@RequestBody request: ResetPasswordRequest): MessageResponse {
        authService.resetPassword(request); return MessageResponse("Contraseña actualizada")
    }
    @PostMapping("/change-password") fun change(authentication: Authentication, @RequestBody request: ChangePasswordRequest): MessageResponse {
        authService.changePassword(authentication.userId(), request); return MessageResponse("Contraseña actualizada")
    }
    @PostMapping("/deactivate") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivate(authentication: Authentication) = authService.deactivate(authentication.userId())
    @PostMapping("/request-deletion") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletion(authentication: Authentication) = authService.requestDeletion(authentication.userId())
    @PostMapping("/cancel-deletion") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelDeletion(authentication: Authentication) = authService.cancelDeletion(authentication.userId())
    @PostMapping("/request-email-change") fun requestEmailChange(authentication:Authentication,@RequestBody request:EmailChangeRequest)=MessageResponse("Se envió un enlace de confirmación al correo nuevo.",authService.requestEmailChange(authentication.userId(),request.email))
    @PostMapping("/confirm-email-change") fun confirmEmailChange(@RequestBody request:TokenRequest):MessageResponse{authService.confirmEmailChange(request.token);return MessageResponse("Correo actualizado. Inicia sesión nuevamente.")}
}
