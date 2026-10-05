package com.eventflow.eventflow_api.application.auth

interface PasswordHasher {
    fun encode(raw: String): String
    fun matches(raw: String, encoded: String): Boolean
}
