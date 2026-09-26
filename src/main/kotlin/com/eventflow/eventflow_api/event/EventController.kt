package com.eventflow.eventflow_api.event

import com.eventflow.eventflow_api.common.userId
import com.eventflow.eventflow_api.domain.EventStatus
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api")
class EventController(private val service: EventService) {
    @GetMapping("/events") fun list(a:Authentication)=service.list(a.userId())
    @PostMapping("/events") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')") fun create(a:Authentication,@RequestBody r:CreateEventRequest)=service.create(a.userId(),r)
    @GetMapping("/events/{id}") fun get(a:Authentication,@PathVariable id:UUID)=service.get(a.userId(),id)
    @PatchMapping("/events/{id}") fun update(a:Authentication,@PathVariable id:UUID,@RequestBody r:UpdateEventRequest)=service.update(a.userId(),id,r)
    @PostMapping("/events/{id}/status/{status}") fun status(a:Authentication,@PathVariable id:UUID,@PathVariable status:EventStatus)=service.transition(a.userId(),id,status)
    @GetMapping("/events/{id}/dashboard") fun dashboard(a:Authentication,@PathVariable id:UUID)=service.dashboard(a.userId(),id)
    @GetMapping("/modules/catalog") fun catalog()=service.catalog()
    @GetMapping("/events/{id}/modules") fun modules(a:Authentication,@PathVariable id:UUID)=service.modules(a.userId(),id)
    @GetMapping("/events/{id}/modules/navigation") fun navigation(a:Authentication,@PathVariable id:UUID)=service.modules(a.userId(),id,true)
    @PutMapping("/events/{id}/modules/{code}") fun configure(a:Authentication,@PathVariable id:UUID,@PathVariable code:String,@RequestBody r:ConfigureModuleRequest)=service.configureModule(a.userId(),id,code,r)
    @PostMapping("/events/{id}/collaborators") @ResponseStatus(HttpStatus.CREATED) fun collaborator(a:Authentication,@PathVariable id:UUID,@RequestBody r:CollaboratorRequest)=service.addCollaborator(a.userId(),id,r)
    @GetMapping("/events/{id}/collaborators") fun collaborators(a:Authentication,@PathVariable id:UUID)=service.listCollaborators(a.userId(),id)
    @DeleteMapping("/events/{id}/collaborators/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT) fun removeCollaborator(a:Authentication,@PathVariable id:UUID,@PathVariable userId:UUID)=service.removeCollaborator(a.userId(),id,userId)
}
