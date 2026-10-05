package com.eventflow.eventflow_api.networking.infrastructure.web

import com.eventflow.eventflow_api.networking.application.NetworkingQrService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/networking") class NetworkingQrController(private val service:NetworkingQrService){
    @GetMapping("/qr") fun qr(a:Authentication,@PathVariable eventId:UUID)=service.qr(a.userId(),eventId)
    @GetMapping("/profiles/{profileId}") fun scan(a:Authentication,@PathVariable eventId:UUID,@PathVariable profileId:UUID)=
        service.scan(a.userId(),eventId,profileId)
}
