package com.eventflow.eventflow_api.capacity.infrastructure.web

import com.eventflow.eventflow_api.capacity.application.CapacityService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/capacity") class CapacityController(private val service:CapacityService){
    @GetMapping("/zones") fun zones(a:Authentication,@PathVariable eventId:UUID)=service.zones(a.userId(),eventId)
    @GetMapping("/services") fun services(a:Authentication,@PathVariable eventId:UUID)=service.services(a.userId(),eventId)
}
