package com.eventflow.eventflow_api.gamification.infrastructure.web

import com.eventflow.eventflow_api.gamification.application.GamificationService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/gamification") class GamificationController(private val service:GamificationService){
    @GetMapping("/progress") fun progress(a:Authentication,@PathVariable eventId:UUID)=service.progress(a.userId(),eventId)
    @PostMapping("/sync") fun sync(a:Authentication,@PathVariable eventId:UUID)=service.syncBadges(a.userId(),eventId)
    @PostMapping("/milestones/{milestoneId}/guests/{invitationId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun complete(a:Authentication,@PathVariable eventId:UUID,@PathVariable milestoneId:UUID,@PathVariable invitationId:UUID)=
        service.completeMilestone(a.userId(),eventId,milestoneId,invitationId)
}
