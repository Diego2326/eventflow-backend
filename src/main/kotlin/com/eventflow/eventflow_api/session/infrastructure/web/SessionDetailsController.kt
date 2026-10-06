package com.eventflow.eventflow_api.session.infrastructure.web

import com.eventflow.eventflow_api.session.application.SessionDetailsService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/sessions") class SessionDetailsController(private val service:SessionDetailsService){
    @GetMapping("/{id}") fun details(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.details(a.userId(),eventId,id)
}
