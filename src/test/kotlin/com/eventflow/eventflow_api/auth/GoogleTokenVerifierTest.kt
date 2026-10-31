package com.eventflow.eventflow_api.auth

import com.eventflow.eventflow_api.auth.infrastructure.identity.GoogleTokenVerifier
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.time.Instant
import java.util.Date

class GoogleTokenVerifierTest {
    @Test
    fun `verifies key signature issuer expiry audience and verified email from JWK`() {
        val trustedKey = RSAKeyGenerator(2048).keyID("trusted-key").generate()
        val otherKey = RSAKeyGenerator(2048).keyID("other-key").generate()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/jwks") { exchange ->
            val body = JWKSet(trustedKey.toPublicJWK()).toString().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val verifier = GoogleTokenVerifier("web-client", "android-client,ios-client",
                "http://127.0.0.1:${server.address.port}/jwks")
            val valid = token(trustedKey, "accounts.google.com", "android-client", true)
            val identity = verifier.verify(valid)
            assertNotNull(identity)
            assertEquals("google-user-1", identity!!.subject)
            assertEquals("member@example.com", identity.email)
            assertNull(verifier.verify(token(otherKey, "https://accounts.google.com", "android-client", true)))
            assertNull(verifier.verify(token(trustedKey, "attacker.example", "android-client", true)))
            assertNull(verifier.verify(token(trustedKey, "https://accounts.google.com", "unregistered-client", true)))
            assertNull(verifier.verify(token(trustedKey, "https://accounts.google.com", "ios-client", false)))
            assertNull(verifier.verify(token(trustedKey, "https://accounts.google.com", "web-client", true, -60)))
        } finally {
            server.stop(0)
        }
    }

    private fun token(key: com.nimbusds.jose.jwk.RSAKey, issuer: String, audience: String, emailVerified: Boolean, expiryOffsetSeconds: Long = 120): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder().issuer(issuer).subject("google-user-1").audience(audience)
            .issueTime(Date.from(now.minusSeconds(5))).expirationTime(Date.from(now.plusSeconds(expiryOffsetSeconds)))
            .claim("email", "Member@Example.com").claim("email_verified", emailVerified)
            .claim("name", "Member").build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).type(JOSEObjectType.JWT).build(), claims)
            .apply { sign(RSASSASigner(key)) }.serialize()
    }
}
