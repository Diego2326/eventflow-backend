package com.eventflow.eventflow_api.auth.application

interface PasswordHasher {
    fun encode(raw: String): String
    fun matches(raw: String, encoded: String): Boolean
}
