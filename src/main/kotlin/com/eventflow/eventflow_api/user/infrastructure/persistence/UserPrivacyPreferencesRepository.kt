package com.eventflow.eventflow_api.user.infrastructure.persistence

import com.eventflow.eventflow_api.user.application.port.UserPrivacyPreferencesPort
import com.eventflow.eventflow_api.user.domain.UserPrivacyPreferences
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserPrivacyPreferencesRepository:JpaRepository<UserPrivacyPreferences,UUID>,UserPrivacyPreferencesPort{
    override fun findByUserId(userId:UUID):UserPrivacyPreferences?
}
