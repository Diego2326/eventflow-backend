package com.eventflow.eventflow_api.user

import com.eventflow.eventflow_api.auth.model.*
import com.eventflow.eventflow_api.auth.repository.*
import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.RoleRequest
import com.eventflow.eventflow_api.domain.RoleRequestRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate

data class ProfileResponse(val id:String,val name:String,val email:String,val phonePrefixId:Int?,val phoneNumber:String?,val profilePictureUrl:String?,val birthDate:LocalDate?,val nationalityCountryCode:String?,val status:UserStatus,val roles:Set<Role>)
data class UpdateProfileRequest(val name:String?=null,val phonePrefixId:Int?=null,val phoneNumber:String?=null,val profilePictureUrl:String?=null,val birthDate:LocalDate?=null,val nationalityCountryCode:String?=null)
data class PreferenceRequest(val channel:String,val enabled:Boolean)
data class RoleRequestDto(val role:Role,val reason:String?=null)

@Service class UserService(private val users:UserRepository,private val preferences:NotificationPreferenceRepository,private val roleRequests:RoleRequestRepository){
    @Transactional(readOnly=true) fun profile(id:java.util.UUID)=users.findById(id).orElseThrow{NotFoundException("Usuario no encontrado")}.toResponse()
    @Transactional fun update(id:java.util.UUID,r:UpdateProfileRequest):ProfileResponse{val u=users.findById(id).orElseThrow{NotFoundException("Usuario no encontrado")};r.name?.let{if(it.isBlank())throw BadRequestException("Nombre inválido") else u.name=it.trim()};r.phonePrefixId?.let{u.phonePrefixId=it};r.phoneNumber?.let{u.phoneNumber=it.trim()};r.profilePictureUrl?.let{u.profilePictureUrl=it};r.birthDate?.let{u.birthDate=it};r.nationalityCountryCode?.let{u.nationalityCountryCode=it.uppercase()};u.updatedAt=Instant.now();return u.toResponse()}
    @Transactional(readOnly=true) fun prefs(id:java.util.UUID)=preferences.findAllByUserId(id)
    @Transactional fun pref(id:java.util.UUID,r:PreferenceRequest)=preferences.save(NotificationPreference(id,r.channel.uppercase(),r.enabled))
    @Transactional fun requestRole(id:java.util.UUID,r:RoleRequestDto):RoleRequest{if(r.role in setOf(Role.USER,Role.ADMIN))throw BadRequestException("Rol no solicitable");return roleRequests.save(RoleRequest(userId=id,requestedRole=r.role.name,reason=r.reason))}
    private fun User.toResponse()=ProfileResponse(requireNotNull(id).toString(),name,email,phonePrefixId,phoneNumber,profilePictureUrl,birthDate,nationalityCountryCode,status,roles)
}

@RestController @RequestMapping("/api/profile")
class UserController(private val service:UserService){
    @GetMapping fun get(a:Authentication)=service.profile(a.userId())
    @PatchMapping fun update(a:Authentication,@RequestBody r:UpdateProfileRequest)=service.update(a.userId(),r)
    @GetMapping("/notification-preferences") fun prefs(a:Authentication)=service.prefs(a.userId())
    @PutMapping("/notification-preferences") fun pref(a:Authentication,@RequestBody r:PreferenceRequest)=service.pref(a.userId(),r)
    @PostMapping("/role-requests") @ResponseStatus(HttpStatus.CREATED) fun role(a:Authentication,@RequestBody r:RoleRequestDto)=service.requestRole(a.userId(),r)
}
