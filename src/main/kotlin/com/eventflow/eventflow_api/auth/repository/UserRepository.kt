package com.eventflow.eventflow_api.auth.repository

import com.eventflow.eventflow_api.auth.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: String): Boolean
}
