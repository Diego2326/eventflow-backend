package com.eventflow.eventflow_api.interaction.infrastructure.web

import com.eventflow.eventflow_api.interaction.application.DrawService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/draws") class DrawController(private val service:DrawService){
    @PostMapping("/{drawId}/run") fun run(a:Authentication,@PathVariable eventId:UUID,@PathVariable drawId:UUID)=service.run(a.userId(),eventId,drawId)
}
