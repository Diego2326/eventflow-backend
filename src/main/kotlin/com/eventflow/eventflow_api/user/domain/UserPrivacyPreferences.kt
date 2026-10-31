package com.eventflow.eventflow_api.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity @Table(name="user_privacy_preference")
class UserPrivacyPreferences(
    @Id @Column(name="user_id") var userId:UUID,
    @Column(name="networking_profile_visible",nullable=false) var networkingProfileVisible:Boolean=true,
    @Column(name="networking_contact_visible",nullable=false) var networkingContactVisible:Boolean=true,
    @Column(name="updated_at",nullable=false) var updatedAt:Instant=Instant.now()
)
