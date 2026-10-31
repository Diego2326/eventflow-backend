package com.eventflow.eventflow_api.notification.infrastructure.web

import com.eventflow.eventflow_api.notification.application.PushDeviceService
import com.eventflow.eventflow_api.notification.application.RegisterPushDeviceRequest
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/profile/push-devices")
class PushDeviceController(private val devices: PushDeviceService) {
    @PostMapping
    fun register(authentication: Authentication, @RequestBody request: RegisterPushDeviceRequest) =
        devices.register(authentication.userId(), request)

    @GetMapping
    fun list(authentication: Authentication) = devices.list(authentication.userId())

    @DeleteMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(authentication: Authentication, @PathVariable deviceId: UUID) = devices.revoke(authentication.userId(), deviceId)
}
