package com.eventflow.eventflow_api.assistance.infrastructure.web

import com.eventflow.eventflow_api.assistance.application.AssistanceRequestDto
import com.eventflow.eventflow_api.assistance.application.AssistanceUpdate
import com.eventflow.eventflow_api.assistance.application.AssistanceService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}") class AssistanceController(private val s:AssistanceService){
    @PostMapping("/assistance") @ResponseStatus(HttpStatus.CREATED) fun assistance(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:AssistanceRequestDto)=s.assistance(a.userId(),eventId,r)
    @GetMapping("/assistance") fun assistanceList(a:Authentication,@PathVariable eventId:UUID)=s.assistanceList(a.userId(),eventId)
    @PatchMapping("/assistance/{id}") fun assistanceUpdate(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:AssistanceUpdate)=s.assistanceUpdate(a.userId(),eventId,id,r)
}
