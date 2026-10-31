package com.eventflow.eventflow_api.event.infrastructure.web

import com.eventflow.eventflow_api.event.application.EventTemplateRequest
import com.eventflow.eventflow_api.event.application.EventTemplateService
import com.eventflow.eventflow_api.event.application.ApplyEventTemplateRequest
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/api/event-templates")
class EventTemplateController(private val service:EventTemplateService){
    @GetMapping fun list(a:Authentication)=service.list(a.userId())
    @GetMapping("/{templateId}") fun get(a:Authentication,@PathVariable templateId:String)=service.get(a.userId(),templateId)
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    fun create(a:Authentication,@RequestBody request:EventTemplateRequest)=service.create(a.userId(),request)
    @PutMapping("/{templateId}") @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    fun update(a:Authentication,@PathVariable templateId:String,@RequestBody request:EventTemplateRequest)=service.update(a.userId(),templateId,request)
    @DeleteMapping("/{templateId}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    fun delete(a:Authentication,@PathVariable templateId:String)=service.delete(a.userId(),templateId)
    @PostMapping("/apply/{templateId}") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    fun apply(a:Authentication,@PathVariable templateId:String,@RequestBody request:ApplyEventTemplateRequest)=service.apply(a.userId(),templateId,request)
}
