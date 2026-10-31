package com.eventflow.eventflow_api.shared.infrastructure.web

import org.springframework.stereotype.Controller
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import java.net.URI

@Controller
class HomeController {
    @GetMapping("/")
    fun home(): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create("/swagger-ui"))
        .build()
}
