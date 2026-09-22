package com.eventflow.eventflow_api.auth.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

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
    var nationalityCountryCode: String? = null
)
