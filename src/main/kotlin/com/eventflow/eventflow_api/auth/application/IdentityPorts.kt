package com.eventflow.eventflow_api.auth.application

data class GoogleIdentity(val subject: String, val email: String, val name: String?, val pictureUrl: String?)

interface GoogleIdentityVerifier {
    val configured: Boolean
    fun verify(idToken: String): GoogleIdentity?
}

interface AccountMessageSender {
    fun sendLink(to: String, subject: String, link: String)
}
