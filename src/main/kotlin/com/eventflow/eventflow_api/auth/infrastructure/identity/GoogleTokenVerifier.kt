package com.eventflow.eventflow_api.auth.infrastructure.identity

import com.eventflow.eventflow_api.auth.application.GoogleIdentity
import com.eventflow.eventflow_api.auth.application.GoogleIdentityVerifier

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GoogleTokenVerifier(@Value("\${app.google.client-id:}") private val clientId: String) : GoogleIdentityVerifier {
    override val configured: Boolean get() = clientId.isNotBlank()
    override fun verify(idToken: String): GoogleIdentity? {
        if (!configured) return null
        @Suppress("UNCHECKED_CAST")
        val info = try {
            RestClient.create().get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
                .retrieve().body(Map::class.java) as? Map<String, Any?>
        } catch (_: Exception) { null } ?: return null
        if (info["aud"]?.toString() != clientId || info["email_verified"]?.toString() != "true") return null
        return GoogleIdentity(
            subject = info["sub"]?.toString() ?: return null,
            email = info["email"]?.toString()?.lowercase() ?: return null,
            name = info["name"]?.toString(), pictureUrl = info["picture"]?.toString()
        )
    }
}
