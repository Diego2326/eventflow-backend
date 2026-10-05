package com.eventflow.eventflow_api.resource.infrastructure.web

import com.eventflow.eventflow_api.resource.application.ResourceSharingService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/resources") class ResourceSharingController(private val service:ResourceSharingService){
    @GetMapping("/{id}/qr") fun qr(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.qr(a.userId(),eventId,id)
    @GetMapping("/{id}") fun get(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.get(a.userId(),eventId,id)
    @PostMapping("/{id}/save") @ResponseStatus(HttpStatus.CREATED) fun save(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.save(a.userId(),eventId,id)
}
