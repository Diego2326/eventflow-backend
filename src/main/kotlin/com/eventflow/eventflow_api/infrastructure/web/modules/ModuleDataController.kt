package com.eventflow.eventflow_api.infrastructure.web.modules

import com.eventflow.eventflow_api.infrastructure.web.common.*
import com.eventflow.eventflow_api.application.modules.*
import com.eventflow.eventflow_api.domain.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/module-data") class ModuleDataController(private val s:ModuleDataService){
    @PostMapping("/{module}/{type}") @ResponseStatus(HttpStatus.CREATED) fun create(a:Authentication,@PathVariable eventId:UUID,@PathVariable module:String,@PathVariable type:String,@RequestBody r:ModuleRecordRequest)=s.create(a.userId(),eventId,module,type,r)
    @GetMapping("/{module}/{type}") fun list(a:Authentication,@PathVariable eventId:UUID,@PathVariable module:String,@PathVariable type:String)=s.list(a.userId(),eventId,module,type)
    @GetMapping("/records/{id}") fun get(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.get(a.userId(),eventId,id)
    @PatchMapping("/records/{id}") fun update(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:ModuleRecordUpdate)=s.update(a.userId(),eventId,id,r)
    @DeleteMapping("/records/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun archive(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.archive(a.userId(),eventId,id)
    @PostMapping("/records/{id}/actions") @ResponseStatus(HttpStatus.CREATED) fun action(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:ModuleActionRequest)=s.action(a.userId(),eventId,id,r)
    @GetMapping("/records/{id}/actions") fun actions(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.actionList(a.userId(),eventId,id)
}
