package com.eventflow.eventflow_api.auth.application

import java.util.UUID

interface AccessTokenIssuer {
    fun issue(userId: UUID, email: String, name: String, roles: Set<String>, sessionId: UUID): String
}
