package com.eventflow.eventflow_api.sport.infrastructure.web

import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import com.eventflow.eventflow_api.sport.application.MatchResultRequest
import com.eventflow.eventflow_api.sport.application.MatchResultService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/matches") class MatchResultController(private val service:MatchResultService){
    @PostMapping("/{matchId}/result") fun report(a:Authentication,@PathVariable eventId:UUID,@PathVariable matchId:UUID,@RequestBody r:MatchResultRequest)=
        service.report(a.userId(),eventId,matchId,r)
}
