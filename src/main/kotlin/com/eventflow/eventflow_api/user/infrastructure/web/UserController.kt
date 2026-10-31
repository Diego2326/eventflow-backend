package com.eventflow.eventflow_api.user.infrastructure.web

import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import com.eventflow.eventflow_api.user.application.PreferenceRequest
import com.eventflow.eventflow_api.user.application.PrivacyPreferencesRequest
import com.eventflow.eventflow_api.user.application.RoleRequestDto
import com.eventflow.eventflow_api.user.application.UpdateProfileRequest
import com.eventflow.eventflow_api.user.application.UserService

import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/api/profile")
class UserController(private val service:UserService){
    @GetMapping fun get(a:Authentication)=service.profile(a.userId())
    @PatchMapping fun update(a:Authentication,@RequestBody r:UpdateProfileRequest)=service.update(a.userId(),r)
    @GetMapping("/notification-preferences") fun prefs(a:Authentication)=service.prefs(a.userId())
    @PutMapping("/notification-preferences") fun pref(a:Authentication,@RequestBody r:PreferenceRequest)=service.pref(a.userId(),r)
    @GetMapping("/privacy") fun privacy(a:Authentication)=service.privacy(a.userId())
    @PutMapping("/privacy") fun updatePrivacy(a:Authentication,@RequestBody r:PrivacyPreferencesRequest)=service.updatePrivacy(a.userId(),r)
    @PostMapping("/role-requests") @ResponseStatus(HttpStatus.CREATED) fun role(a:Authentication,@RequestBody r:RoleRequestDto)=service.requestRole(a.userId(),r)
    @GetMapping("/sessions") fun sessions(a:Authentication)=service.sessions(a.userId(),a.details as? java.util.UUID)
    @GetMapping("/export") fun export(a:Authentication)=service.export(a.userId(),a.details as? java.util.UUID)
    @DeleteMapping("/sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun revokeSession(a:Authentication,@PathVariable id:java.util.UUID)=service.revokeSession(a.userId(),id)
}
