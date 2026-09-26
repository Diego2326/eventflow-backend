package com.eventflow.eventflow_api.auth.service

import com.eventflow.eventflow_api.auth.dto.*
import com.eventflow.eventflow_api.auth.model.*
import com.eventflow.eventflow_api.auth.repository.*
import com.eventflow.eventflow_api.common.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class RefreshRequest(val refreshToken: String)
data class TokenRequest(val token: String)
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val token: String, val password: String)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String, val closeAllSessions: Boolean = false)
data class GoogleLoginRequest(val idToken: String)
data class EmailChangeRequest(val email: String)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val tokenRepository: AuthTokenRepository,
    private val sessionRepository: UserSessionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val mailSenderProvider: ObjectProvider<JavaMailSender>,
    @Value("\${app.frontend-url:http://localhost:8081}") private val frontendUrl: String,
    @Value("\${app.auth.expose-tokens:false}") private val exposeTokens: Boolean,
    @Value("\${app.google.client-id:}") private val googleClientId: String
) {
    private val random = SecureRandom()

    @Transactional
    fun register(request: RegisterRequest): String? {
        val email = request.email.trim().lowercase()
        if (userRepository.existsByEmail(email)) throw ConflictException("El correo ya fue registrado")
        if ((request.phoneNumber ?: request.phone).isNullOrBlank()) throw BadRequestException("El teléfono es obligatorio")
        val user = userRepository.save(User(name = request.name.trim(), email = email,
            phonePrefixId = request.phonePrefixId, phoneNumber = (request.phoneNumber ?: request.phone)?.trim()?.ifBlank { null },
            passwordHash = requireNotNull(passwordEncoder.encode(request.password)), birthDate = request.birthDate,
            nationalityCountryCode = request.nationalityCountryCode?.uppercase()))
        val raw = issueToken(requireNotNull(user.id), AuthTokenType.EMAIL_VERIFICATION, Duration.ofHours(24))
        sendLink(email, "Activa tu cuenta de EventFlow", "$frontendUrl/verify-email?token=$raw")
        return raw.takeIf { exposeTokens }
    }

    @Transactional
    fun verifyEmail(raw: String) {
        val token = validToken(raw, AuthTokenType.EMAIL_VERIFICATION)
        val user = userRepository.findById(token.userId).orElseThrow { NotFoundException("Usuario no encontrado") }
        user.status = UserStatus.ACTIVE; user.emailVerifiedAt = Instant.now(); user.updatedAt = Instant.now(); token.usedAt = Instant.now()
    }

    @Transactional
    fun resendVerification(email: String): String? {
        val user = userRepository.findByEmail(email.trim().lowercase()) ?: return null
        if (user.status != UserStatus.PENDING_VERIFICATION) return null
        tokenRepository.revokeActive(requireNotNull(user.id), AuthTokenType.EMAIL_VERIFICATION, Instant.now())
        val raw = issueToken(requireNotNull(user.id), AuthTokenType.EMAIL_VERIFICATION, Duration.ofHours(24))
        sendLink(user.email, "Activa tu cuenta de EventFlow", "$frontendUrl/verify-email?token=$raw")
        return raw.takeIf { exposeTokens }
    }

    @Transactional
    fun login(request: LoginRequest, userAgent: String?, ip: String?): AuthResponse {
        val identifier = request.identifier.trim().lowercase()
        val user = if (identifier.contains('@')) userRepository.findByEmail(identifier) else userRepository.findByInternationalPhone(identifier) ?: userRepository.findByPhoneNumber(identifier)
        if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) throw UnauthorizedException("Correo/teléfono o contraseña incorrectos")
        if (user.status !in setOf(UserStatus.ACTIVE, UserStatus.DELETION_PENDING)) throw UnauthorizedException("La cuenta no está activa")
        return createSession(user, userAgent, ip)
    }

    @Transactional
    fun googleLogin(request: GoogleLoginRequest, userAgent: String?, ip: String?): AuthResponse {
        if (googleClientId.isBlank()) throw BadRequestException("Google OAuth no está configurado")
        @Suppress("UNCHECKED_CAST")
        val info = try { RestClient.create().get().uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", request.idToken).retrieve().body(Map::class.java) as? Map<String, Any?> }
            catch (_: Exception) { null } ?: throw UnauthorizedException("Token de Google inválido")
        if (info["aud"]?.toString() != googleClientId || info["email_verified"]?.toString() != "true") throw UnauthorizedException("Token de Google inválido")
        val subject = info["sub"]?.toString() ?: throw UnauthorizedException("Token de Google inválido")
        val email = info["email"]?.toString()?.lowercase() ?: throw UnauthorizedException("Google no proporcionó correo")
        var user = userRepository.findByGoogleSubject(subject) ?: userRepository.findByEmail(email)
        if (user == null) {
            user = userRepository.save(User(name = info["name"]?.toString() ?: email.substringBefore('@'), email = email,
                passwordHash = requireNotNull(passwordEncoder.encode(randomToken())), status = UserStatus.ACTIVE,
                emailVerifiedAt = Instant.now(), googleSubject = subject, profilePictureUrl = info["picture"]?.toString()))
        } else {
            if (user.googleSubject != null && user.googleSubject != subject) throw ConflictException("El correo ya está vinculado a otra cuenta Google")
            user.googleSubject = subject
            if (user.status == UserStatus.PENDING_VERIFICATION) { user.status = UserStatus.ACTIVE; user.emailVerifiedAt = Instant.now() }
        }
        return createSession(user, userAgent, ip)
    }

    @Transactional
    fun refresh(rawRefreshToken: String): AuthResponse {
        val session = sessionRepository.findByRefreshTokenHash(hash(rawRefreshToken)) ?: throw UnauthorizedException("Refresh token inválido")
        if (session.revokedAt != null || session.expiresAt.isBefore(Instant.now())) throw UnauthorizedException("Refresh token revocado o expirado")
        val user = userRepository.findById(session.userId).orElseThrow { UnauthorizedException("Sesión inválida") }
        if (user.status !in setOf(UserStatus.ACTIVE, UserStatus.DELETION_PENDING)) throw UnauthorizedException("La cuenta no está activa")
        session.revokedAt = Instant.now()
        return createSession(user, session.userAgent, session.ipAddress)
    }

    @Transactional
    fun logout(userId: UUID, sessionId: UUID?, all: Boolean) {
        if (all) sessionRepository.revokeAll(userId, Instant.now())
        else sessionId?.let { sessionRepository.findById(it).ifPresent { s -> if (s.userId == userId) s.revokedAt = Instant.now() } }
    }

    @Transactional
    fun forgotPassword(email: String): String? {
        val user = userRepository.findByEmail(email.trim().lowercase()) ?: return null
        tokenRepository.revokeActive(requireNotNull(user.id), AuthTokenType.PASSWORD_RESET, Instant.now())
        val raw = issueToken(requireNotNull(user.id), AuthTokenType.PASSWORD_RESET, Duration.ofMinutes(30))
        sendLink(user.email, "Restablece tu contraseña de EventFlow", "$frontendUrl/reset-password?token=$raw")
        return raw.takeIf { exposeTokens }
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest) {
        validatePassword(request.password)
        val token = validToken(request.token, AuthTokenType.PASSWORD_RESET)
        val user = userRepository.findById(token.userId).orElseThrow { NotFoundException("Usuario no encontrado") }
        user.passwordHash = requireNotNull(passwordEncoder.encode(request.password)); user.updatedAt = Instant.now(); token.usedAt = Instant.now()
        sessionRepository.revokeAll(requireNotNull(user.id), Instant.now())
    }

    @Transactional
    fun changePassword(userId: UUID, request: ChangePasswordRequest) {
        validatePassword(request.newPassword)
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("Usuario no encontrado") }
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) throw UnauthorizedException("La contraseña actual es incorrecta")
        user.passwordHash = requireNotNull(passwordEncoder.encode(request.newPassword)); user.updatedAt = Instant.now()
        if (request.closeAllSessions) sessionRepository.revokeAll(userId, Instant.now())
    }

    @Transactional fun deactivate(userId: UUID) = setStatusAndRevoke(userId, UserStatus.DISABLED)
    @Transactional fun requestDeletion(userId: UUID) {
        val user = user(userId); user.status = UserStatus.DELETION_PENDING; user.deletionRequestedAt = Instant.now(); user.updatedAt = Instant.now()
    }
    @Transactional fun cancelDeletion(userId: UUID) {
        val user = user(userId)
        if (user.status != UserStatus.DELETION_PENDING) throw ConflictException("La cuenta no tiene eliminación pendiente")
        user.status = UserStatus.ACTIVE; user.deletionRequestedAt = null; user.updatedAt = Instant.now()
    }

    @Transactional fun requestEmailChange(userId:UUID,emailRaw:String):String?{val email=emailRaw.trim().lowercase();if(!email.contains('@'))throw BadRequestException("Correo inválido");if(userRepository.existsByEmail(email))throw ConflictException("El correo ya está registrado");val user=user(userId);user.pendingEmail=email;tokenRepository.revokeActive(userId,AuthTokenType.EMAIL_CHANGE,Instant.now());val raw=issueToken(userId,AuthTokenType.EMAIL_CHANGE,Duration.ofHours(2));sendLink(email,"Confirma tu nuevo correo de EventFlow","$frontendUrl/confirm-email-change?token=$raw");return raw.takeIf{exposeTokens}}
    @Transactional fun confirmEmailChange(raw:String){val token=validToken(raw,AuthTokenType.EMAIL_CHANGE);val user=user(token.userId);val email=user.pendingEmail?:throw ConflictException("No existe cambio de correo pendiente");if(userRepository.existsByEmail(email))throw ConflictException("El correo ya está registrado");user.email=email;user.pendingEmail=null;user.emailVerifiedAt=Instant.now();user.updatedAt=Instant.now();token.usedAt=Instant.now();sessionRepository.revokeAll(requireNotNull(user.id),Instant.now())}

    private fun setStatusAndRevoke(userId: UUID, status: UserStatus) { val user=user(userId); user.status=status; user.updatedAt=Instant.now(); sessionRepository.revokeAll(userId, Instant.now()) }
    private fun user(id: UUID) = userRepository.findById(id).orElseThrow { NotFoundException("Usuario no encontrado") }
    private fun createSession(user: User, userAgent: String?, ip: String?): AuthResponse {
        val refresh = randomToken()
        val session = sessionRepository.save(UserSession(userId = requireNotNull(user.id), refreshTokenHash = hash(refresh),
            expiresAt = Instant.now().plus(Duration.ofDays(30)), userAgent = userAgent?.take(500), ipAddress = ip?.take(64)))
        return AuthResponse(jwtService.generateToken(user, requireNotNull(session.id)), refresh, userId = requireNotNull(user.id),
            name = user.name, email = user.email, roles = user.roles.map { it.name }.toSet())
    }
    private fun issueToken(userId: UUID, type: AuthTokenType, duration: Duration): String {
        val raw = randomToken(); tokenRepository.save(AuthToken(userId = userId, tokenHash = hash(raw), type = type, expiresAt = Instant.now().plus(duration))); return raw
    }
    private fun validToken(raw: String, type: AuthTokenType): AuthToken {
        val token = tokenRepository.findByTokenHashAndType(hash(raw), type) ?: throw UnauthorizedException("Token inválido")
        if (token.usedAt != null || token.revokedAt != null || token.expiresAt.isBefore(Instant.now())) throw UnauthorizedException("Token expirado o utilizado")
        return token
    }
    private fun sendLink(to: String, subject: String, link: String) {
        mailSenderProvider.ifAvailable?.send(SimpleMailMessage().apply { setTo(to); setSubject(subject); text = "Abre este enlace seguro: $link" })
    }
    private fun validatePassword(value: String) {
        if (value.length !in 8..72 || !value.any(Char::isUpperCase) || !value.any(Char::isLowerCase) || !value.any(Char::isDigit) || value.all(Char::isLetterOrDigit)) throw IllegalArgumentException("La contraseña debe tener 8-72 caracteres e incluir mayúscula, minúscula, número y símbolo")
    }
    private fun randomToken(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
