package com.eventflow.eventflow_api.user.infrastructure.web

import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import com.eventflow.eventflow_api.user.application.PreferenceRequest
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
    @PostMapping("/role-requests") @ResponseStatus(HttpStatus.CREATED) fun role(a:Authentication,@RequestBody r:RoleRequestDto)=service.requestRole(a.userId(),r)
}
