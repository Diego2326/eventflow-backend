package com.eventflow.eventflow_api.user.application

import com.eventflow.eventflow_api.admin.application.port.RoleRequestRepositoryPort
import com.eventflow.eventflow_api.admin.domain.RoleRequest
import com.eventflow.eventflow_api.auth.application.port.NotificationPreferenceRepositoryPort
import com.eventflow.eventflow_api.auth.application.port.UserRepositoryPort
import com.eventflow.eventflow_api.auth.application.port.UserSessionRepositoryPort
import com.eventflow.eventflow_api.auth.domain.UserSession
import com.eventflow.eventflow_api.auth.domain.NotificationPreference
import com.eventflow.eventflow_api.auth.domain.Role
import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.domain.UserStatus
import com.eventflow.eventflow_api.user.application.port.UserPrivacyPreferencesPort
import com.eventflow.eventflow_api.user.domain.UserPrivacyPreferences
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

data class ProfileResponse(val id:String,val name:String,val email:String,val phonePrefixId:Int?,val phoneNumber:String?,val profilePictureUrl:String?,val birthDate:LocalDate?,val nationalityCountryCode:String?,val status:UserStatus,val roles:Set<Role>,val profileComplete:Boolean)
data class UpdateProfileRequest(val name:String?=null,val phonePrefixId:Int?=null,val phoneNumber:String?=null,val profilePictureUrl:String?=null,val birthDate:LocalDate?=null,val nationalityCountryCode:String?=null)
data class PreferenceRequest(val channel:String,val enabled:Boolean)
data class RoleRequestDto(val role:Role,val reason:String?=null)
data class SessionResponse(val id:java.util.UUID,val createdAt:Instant,val lastUsedAt:Instant,val expiresAt:Instant,val revokedAt:Instant?,val current:Boolean,val userAgent:String?)
data class AccountExport(val profile:ProfileResponse,val notificationPreferences:List<NotificationPreference>,val sessions:List<SessionResponse>,val exportedAt:Instant)
data class PrivacyPreferencesRequest(val networkingProfileVisible:Boolean,val networkingContactVisible:Boolean)
data class PrivacyPreferencesResponse(val networkingProfileVisible:Boolean,val networkingContactVisible:Boolean,val updatedAt:Instant?)

@Service class UserService(private val users:UserRepositoryPort,private val preferences:NotificationPreferenceRepositoryPort,private val roleRequests:RoleRequestRepositoryPort,private val sessions:UserSessionRepositoryPort,private val privacy:UserPrivacyPreferencesPort){
    @Transactional(readOnly=true) fun profile(id:java.util.UUID)=users.findById(id).orElseThrow{NotFoundException("Usuario no encontrado")}.toResponse()
    @Transactional fun update(id:java.util.UUID,r:UpdateProfileRequest):ProfileResponse{val u=users.findById(id).orElseThrow{NotFoundException("Usuario no encontrado")};r.name?.let{if(it.isBlank())throw BadRequestException("Nombre inválido") else u.name=it.trim()};r.phonePrefixId?.let{u.phonePrefixId=it};r.phoneNumber?.let{u.phoneNumber=it.trim()};r.profilePictureUrl?.let{u.profilePictureUrl=it};r.birthDate?.let{u.birthDate=it};r.nationalityCountryCode?.let{u.nationalityCountryCode=it.uppercase()};u.updatedAt=Instant.now();return u.toResponse()}
    @Transactional(readOnly=true) fun prefs(id:java.util.UUID)=preferences.findAllByUserId(id)
    @Transactional fun pref(id:java.util.UUID,r:PreferenceRequest)=preferences.save(NotificationPreference(id,r.channel.uppercase(),r.enabled))
    @Transactional(readOnly=true) fun privacy(id:java.util.UUID):PrivacyPreferencesResponse=privacy.findByUserId(id)?.let{PrivacyPreferencesResponse(it.networkingProfileVisible,it.networkingContactVisible,it.updatedAt)}?:PrivacyPreferencesResponse(true,true,null)
    @Transactional fun updatePrivacy(id:java.util.UUID,r:PrivacyPreferencesRequest):PrivacyPreferencesResponse{val saved=privacy.save(UserPrivacyPreferences(id,r.networkingProfileVisible,r.networkingContactVisible,Instant.now()));return PrivacyPreferencesResponse(saved.networkingProfileVisible,saved.networkingContactVisible,saved.updatedAt)}
    @Transactional fun requestRole(id:java.util.UUID,r:RoleRequestDto):RoleRequest{if(r.role in setOf(Role.USER,Role.ADMIN))throw BadRequestException("Rol no solicitable");return roleRequests.save(RoleRequest(userId=id,requestedRole=r.role.name,reason=r.reason))}
    @Transactional(readOnly=true) fun sessions(userId:java.util.UUID,currentSessionId:java.util.UUID?)=sessions.findAllByUserIdOrderByCreatedAtDesc(userId).map{it.toResponse(currentSessionId)}
    @Transactional(readOnly=true) fun export(userId:java.util.UUID,currentSessionId:java.util.UUID?)=AccountExport(profile(userId),preferences.findAllByUserId(userId),sessions(userId,currentSessionId),Instant.now())
    @Transactional fun revokeSession(userId:java.util.UUID,sessionId:java.util.UUID){val session=sessions.findById(sessionId).orElseThrow{NotFoundException("Sesión no encontrada")};if(session.userId!=userId)throw NotFoundException("Sesión no encontrada");if(session.revokedAt==null)session.revokedAt=Instant.now()}
    private fun UserSession.toResponse(currentId:java.util.UUID?)=SessionResponse(requireNotNull(id),createdAt,lastUsedAt,expiresAt,revokedAt,id==currentId,userAgent)
    private fun User.toResponse()=ProfileResponse(requireNotNull(id).toString(),name,email,phonePrefixId,phoneNumber,profilePictureUrl,birthDate,nationalityCountryCode,status,roles,!phoneNumber.isNullOrBlank())
}

