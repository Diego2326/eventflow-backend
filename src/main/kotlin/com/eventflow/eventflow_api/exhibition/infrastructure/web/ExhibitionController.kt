package com.eventflow.eventflow_api.exhibition.infrastructure.web

import com.eventflow.eventflow_api.exhibition.application.ExhibitionService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/exhibitors") class ExhibitionController(private val service:ExhibitionService){
    @GetMapping("/{id}") fun details(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.details(a.userId(),eventId,id)
}
