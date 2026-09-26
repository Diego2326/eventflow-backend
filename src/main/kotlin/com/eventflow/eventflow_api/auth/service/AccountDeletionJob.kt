package com.eventflow.eventflow_api.auth.service

import com.eventflow.eventflow_api.auth.model.Role
import com.eventflow.eventflow_api.auth.model.UserStatus
import com.eventflow.eventflow_api.auth.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AccountDeletionJob(private val users:UserRepository,private val encoder:PasswordEncoder,@Value("\${app.auth.deletion-grace-days:30}") private val graceDays:Long){
    @Scheduled(cron="0 20 3 * * *") @Transactional
    fun anonymizeExpired(){val cutoff=Instant.now().minus(graceDays,ChronoUnit.DAYS);users.findAll().filter{it.status==UserStatus.DELETION_PENDING&&it.deletionRequestedAt?.isBefore(cutoff)==true}.forEach{u->val id=requireNotNull(u.id);u.name="Usuario eliminado";u.email="deleted+$id@invalid.eventflow";u.phonePrefixId=null;u.phoneNumber=null;u.profilePictureUrl=null;u.birthDate=null;u.nationalityCountryCode=null;u.googleSubject=null;u.passwordHash=requireNotNull(encoder.encode(UUID.randomUUID().toString()));u.roles=mutableSetOf(Role.USER);u.status=UserStatus.DELETED;u.updatedAt=Instant.now()}}
}
