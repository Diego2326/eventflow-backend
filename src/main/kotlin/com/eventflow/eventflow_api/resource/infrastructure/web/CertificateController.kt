package com.eventflow.eventflow_api.resource.infrastructure.web

import com.eventflow.eventflow_api.resource.application.CertificateIssueRequest
import com.eventflow.eventflow_api.resource.application.CertificateService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/certificates") class CertificateController(private val service:CertificateService){
    @PostMapping @ResponseStatus(HttpStatus.CREATED) fun issue(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:CertificateIssueRequest)=service.issue(a.userId(),eventId,r)
    @GetMapping("/mine") fun mine(a:Authentication,@PathVariable eventId:UUID)=service.mine(a.userId(),eventId)
}
