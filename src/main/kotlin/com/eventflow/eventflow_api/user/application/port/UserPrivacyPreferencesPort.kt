package com.eventflow.eventflow_api.user.application.port

import com.eventflow.eventflow_api.user.domain.UserPrivacyPreferences
import java.util.UUID

interface UserPrivacyPreferencesPort{
    fun findByUserId(userId:UUID):UserPrivacyPreferences?
    fun save(preferences:UserPrivacyPreferences):UserPrivacyPreferences
}
