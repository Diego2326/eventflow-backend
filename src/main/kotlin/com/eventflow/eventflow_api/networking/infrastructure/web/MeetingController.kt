package com.eventflow.eventflow_api.networking.infrastructure.web

import com.eventflow.eventflow_api.networking.application.MeetingRequest
import com.eventflow.eventflow_api.networking.application.MeetingService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/networking/meetings") class MeetingController(private val service:MeetingService){
    @PostMapping @ResponseStatus(HttpStatus.CREATED) fun request(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:MeetingRequest)=service.request(a.userId(),eventId,r)
    @GetMapping fun mine(a:Authentication,@PathVariable eventId:UUID)=service.mine(a.userId(),eventId)
    @PostMapping("/{id}/accept") fun accept(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.decide(a.userId(),eventId,id,true)
    @PostMapping("/{id}/decline") fun decline(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.decide(a.userId(),eventId,id,false)
    @PostMapping("/{id}/cancel") fun cancel(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.cancel(a.userId(),eventId,id)
}
