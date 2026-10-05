package com.eventflow.eventflow_api.agenda.infrastructure.web

import com.eventflow.eventflow_api.agenda.application.AgendaRequest
import com.eventflow.eventflow_api.agenda.application.AgendaService
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}") class AgendaController(private val s:AgendaService){
    @PostMapping("/agenda") @ResponseStatus(HttpStatus.CREATED) fun add(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:AgendaRequest)=s.addAgenda(a.userId(),eventId,r)
    @GetMapping("/agenda") fun agenda(a:Authentication,@PathVariable eventId:UUID)=s.agenda(a.userId(),eventId)
    @GetMapping("/agenda/now-next") fun now(a:Authentication,@PathVariable eventId:UUID)=s.nowNext(a.userId(),eventId)
    @GetMapping("/agenda/mine") fun mine(a:Authentication,@PathVariable eventId:UUID)=s.myAgenda(a.userId(),eventId)
    @PutMapping("/agenda/{id}") fun update(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:AgendaRequest)=s.updateAgenda(a.userId(),eventId,id,r)
    @DeleteMapping("/agenda/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun delete(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.deleteAgenda(a.userId(),eventId,id)
    @PutMapping("/agenda/{id}/favorite") @ResponseStatus(HttpStatus.NO_CONTENT) fun favorite(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestParam(defaultValue="true") enabled:Boolean)=s.favorite(a.userId(),eventId,id,enabled)
}
