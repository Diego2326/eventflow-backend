package com.eventflow.eventflow_api.transport.infrastructure.web

import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import com.eventflow.eventflow_api.transport.application.TransportRouteService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/transport/routes") class TransportRouteController(private val service:TransportRouteService){
    @GetMapping("/{id}") fun details(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.details(a.userId(),eventId,id)
}
