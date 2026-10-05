package com.eventflow.eventflow_api.infrastructure.web.operations

import com.eventflow.eventflow_api.infrastructure.web.common.*
import com.eventflow.eventflow_api.application.operations.*
import com.eventflow.eventflow_api.domain.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}") class OperationsController(private val s:OperationsService){
    @PostMapping("/agenda") @ResponseStatus(HttpStatus.CREATED) fun add(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:AgendaRequest)=s.addAgenda(a.userId(),eventId,r)
    @GetMapping("/agenda") fun agenda(a:Authentication,@PathVariable eventId:UUID)=s.agenda(a.userId(),eventId)
    @GetMapping("/agenda/now-next") fun now(a:Authentication,@PathVariable eventId:UUID)=s.nowNext(a.userId(),eventId)
    @GetMapping("/agenda/mine") fun mine(a:Authentication,@PathVariable eventId:UUID)=s.myAgenda(a.userId(),eventId)
    @PutMapping("/agenda/{id}") fun update(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:AgendaRequest)=s.updateAgenda(a.userId(),eventId,id,r)
    @DeleteMapping("/agenda/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun delete(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.deleteAgenda(a.userId(),eventId,id)
    @PutMapping("/agenda/{id}/favorite") @ResponseStatus(HttpStatus.NO_CONTENT) fun favorite(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestParam(defaultValue="true") enabled:Boolean)=s.favorite(a.userId(),eventId,id,enabled)
    @PostMapping("/assistance") @ResponseStatus(HttpStatus.CREATED) fun assistance(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:AssistanceRequestDto)=s.assistance(a.userId(),eventId,r)
    @GetMapping("/assistance") fun assistanceList(a:Authentication,@PathVariable eventId:UUID)=s.assistanceList(a.userId(),eventId)
    @PatchMapping("/assistance/{id}") fun assistanceUpdate(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:AssistanceUpdate)=s.assistanceUpdate(a.userId(),eventId,id,r)
    @PostMapping("/notifications") @ResponseStatus(HttpStatus.CREATED) fun notify(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:NotificationRequest)=s.notify(a.userId(),eventId,r)
    @GetMapping("/notifications") fun notifications(a:Authentication,@PathVariable eventId:UUID)=s.notifications(a.userId(),eventId)
    @PostMapping("/messages") @ResponseStatus(HttpStatus.CREATED) fun message(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:MessageRequest)=s.message(a.userId(),eventId,r)
    @GetMapping("/messages") fun messages(a:Authentication,@PathVariable eventId:UUID,@RequestParam channel:String)=s.messages(a.userId(),eventId,channel)
}
