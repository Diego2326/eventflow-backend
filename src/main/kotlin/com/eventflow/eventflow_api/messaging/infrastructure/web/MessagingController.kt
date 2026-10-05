package com.eventflow.eventflow_api.messaging.infrastructure.web

import com.eventflow.eventflow_api.messaging.application.MessageRequest
import com.eventflow.eventflow_api.messaging.application.MessagingService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}") class MessagingController(private val s:MessagingService){
    @PostMapping("/messages") @ResponseStatus(HttpStatus.CREATED) fun message(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:MessageRequest)=s.message(a.userId(),eventId,r)
    @GetMapping("/messages") fun messages(a:Authentication,@PathVariable eventId:UUID,@RequestParam channel:String)=s.messages(a.userId(),eventId,channel)
}
