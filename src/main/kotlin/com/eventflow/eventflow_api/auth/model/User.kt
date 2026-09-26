package com.eventflow.eventflow_api.auth.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ElementCollection
import jakarta.persistence.CollectionTable
import jakarta.persistence.JoinColumn
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

enum class UserStatus { PENDING_VERIFICATION, ACTIVE, DISABLED, DELETION_PENDING, DELETED }
enum class Role { USER, ORGANIZER, SPACE_OWNER, SERVICE_PROVIDER, STAFF, MODERATOR, COLLABORATOR, ADMIN }

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    var id: UUID? = null,

    @Column(name = "user_name", nullable = false)
    var name: String,

    @Column(name = "user_email", nullable = false, unique = true)
    var email: String,

    @Column(name = "user_pending_email")
    var pendingEmail: String? = null,

    @Column(name = "user_phone_prefix_id")
    var phonePrefixId: Int? = null,

    @Column(name = "user_phone_number", length = 32)
    var phoneNumber: String? = null,

    @Column(name = "user_password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "user_pfp_url", columnDefinition = "text")
    var profilePictureUrl: String? = null,

    @Column(name = "user_birth_date")
    var birthDate: LocalDate? = null,

    @Column(name = "user_nationality_country_code", length = 2)
    var nationalityCountryCode: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false)
    var status: UserStatus = UserStatus.PENDING_VERIFICATION,

    @Column(name = "user_email_verified_at")
    var emailVerifiedAt: Instant? = null,

    @Column(name = "user_google_subject", unique = true)
    var googleSubject: String? = null,

    @Column(name = "user_deletion_requested_at")
    var deletionRequestedAt: Instant? = null,

    @Column(name = "user_created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "user_updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @ElementCollection
    @CollectionTable(name = "user_role", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    var roles: MutableSet<Role> = mutableSetOf(Role.USER)
)
