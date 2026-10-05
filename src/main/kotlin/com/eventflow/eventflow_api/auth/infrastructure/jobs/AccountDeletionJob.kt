package com.eventflow.eventflow_api.auth.infrastructure.jobs

import com.eventflow.eventflow_api.auth.application.PasswordHasher
import com.eventflow.eventflow_api.auth.application.port.UserRepositoryPort
import com.eventflow.eventflow_api.auth.application.port.UserSessionRepositoryPort
import com.eventflow.eventflow_api.auth.domain.Role
import com.eventflow.eventflow_api.auth.domain.UserStatus

import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AccountDeletionJob(private val users:UserRepositoryPort,private val sessions:UserSessionRepositoryPort,private val encoder:PasswordHasher,@Value("\${app.auth.deletion-grace-days:30}") private val graceDays:Long){
    @Scheduled(cron="0 20 3 * * *") @Transactional
    fun anonymizeExpired(){val cutoff=Instant.now().minus(graceDays,ChronoUnit.DAYS);users.findAllByStatusAndDeletionRequestedAtBefore(UserStatus.DELETION_PENDING,cutoff).forEach{u->val id=requireNotNull(u.id);sessions.revokeAll(id,Instant.now());u.name="Usuario eliminado";u.email="deleted+$id@invalid.eventflow";u.pendingEmail=null;u.phonePrefixId=null;u.phoneNumber=null;u.profilePictureUrl=null;u.birthDate=null;u.nationalityCountryCode=null;u.googleSubject=null;u.passwordHash=requireNotNull(encoder.encode(UUID.randomUUID().toString()));u.roles=mutableSetOf(Role.USER);u.status=UserStatus.DELETED;u.updatedAt=Instant.now()}}
}
