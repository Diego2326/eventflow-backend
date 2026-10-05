package com.eventflow.eventflow_api.session.infrastructure.web

import com.eventflow.eventflow_api.session.application.SessionAttendanceService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/sessions/{sessionId}/attendance")
class SessionAttendanceController(private val service:SessionAttendanceService){
    @PostMapping("/{invitationId}") @ResponseStatus(HttpStatus.CREATED)
    fun checkIn(a:Authentication,@PathVariable eventId:UUID,@PathVariable sessionId:UUID,@PathVariable invitationId:UUID)=
        service.checkIn(a.userId(),eventId,sessionId,invitationId)
}
