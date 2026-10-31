package com.eventflow.eventflow_api.auth.infrastructure.identity

import com.eventflow.eventflow_api.auth.application.GoogleIdentity
import com.eventflow.eventflow_api.auth.application.GoogleIdentityVerifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component

/** Verifies Google's signature/expiry with Google's rotating JWK set, then checks this app's audiences. */
@Component
class GoogleTokenVerifier(
    @Value("\${app.google.client-id:}") legacyClientId: String,
    @Value("\${app.google.client-ids:}") configuredClientIds: String,
    @Value("\${app.google.jwks-uri:https://www.googleapis.com/oauth2/v3/certs}") jwksUri: String
) : GoogleIdentityVerifier {
    private val clientIds = (configuredClientIds.split(',') + legacyClientId)
        .map(String::trim).filter(String::isNotEmpty).toSet()
    private val decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build().apply {
        setJwtValidator { jwt ->
            val timeValidation = JwtValidators.createDefault().validate(jwt)
            if (timeValidation.hasErrors()) return@setJwtValidator timeValidation
            if (jwt.getClaimAsString("iss") !in GOOGLE_ISSUERS) OAuth2TokenValidatorResult.failure(
                org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Invalid Google issuer", null)
            ) else OAuth2TokenValidatorResult.success()
        }
    }

    override val configured: Boolean get() = clientIds.isNotEmpty()

    override fun verify(idToken: String): GoogleIdentity? {
        if (!configured || idToken.isBlank() || idToken.length > 16_384) return null
        val jwt = runCatching { decoder.decode(idToken) }.getOrNull() ?: return null
        val audiences = jwt.audience.orEmpty()
        if (audiences.none(clientIds::contains)) return null
        if (jwt.claims["email_verified"]?.toString()?.equals("true", ignoreCase = true) != true) return null
        val subject = jwt.subject?.takeIf(String::isNotBlank) ?: return null
        val email = jwt.getClaimAsString("email")?.trim()?.lowercase()
            ?.takeIf { it.length <= 320 && it.contains('@') } ?: return null
        return GoogleIdentity(subject, email, jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"))
    }

    companion object {
        private val GOOGLE_ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")
    }
}
