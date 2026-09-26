package com.eventflow.eventflow_api.modules

import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.event.EventService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

data class ModuleRecordRequest(val title:String?=null,val payload:Map<String,Any?> = emptyMap(),val status:String="ACTIVE",val capacity:Int?=null,val startsAt:Instant?=null,val endsAt:Instant?=null,val parentRecordId:UUID?=null)
data class ModuleRecordUpdate(val title:String?=null,val payload:Map<String,Any?>?=null,val status:String?=null,val capacity:Int?=null,val startsAt:Instant?=null,val endsAt:Instant?=null)
data class ModuleActionRequest(val action:String,val payload:Map<String,Any?> = emptyMap(),val unique:Boolean=true,val quantity:Int=1)
data class ModuleRecordResponse(val id:UUID,val eventId:UUID,val moduleCode:String,val recordType:String,val ownerUserId:UUID?,val parentRecordId:UUID?,val status:String,val title:String?,val payload:Map<String,Any?>,val capacity:Int?,val currentCount:Int,val startsAt:Instant?,val endsAt:Instant?,val createdAt:Instant)
data class ModuleActionResponse(val id:UUID,val recordId:UUID,val actorUserId:UUID?,val action:String,val payload:Map<String,Any?>,val createdAt:Instant)

@Service class ModuleDataService(private val records:ModuleRecordRepository,private val actions:ModuleActionRepository,private val events:EventRepository,private val eventService:EventService,private val mapper:ObjectMapper){
    private val guestCreatable=setOf("GAL:PHOTO","NET:PROFILE","NET:MEETING","LNF:LOST_ITEM","REV:SURVEY_RESPONSE","ORD:ORDER")
    private val types=mapOf(
        "MAP" to setOf("ZONE","POINT"),"ORD" to setOf("MENU_CATEGORY","MENU_ITEM","ORDER"),"QUE" to setOf("QUEUE"),"BKG" to setOf("ACTIVITY"),
        "INT" to setOf("POLL","QUESTION_BOARD","TRIVIA","DRAW","GUEST_MESSAGE","SONG"),"GAM" to setOf("PASSPORT","MILESTONE","MISSION","BADGE"),
        "GAL" to setOf("PHOTO"),"NET" to setOf("PROFILE","MEETING"),"EXH" to setOf("EXHIBITOR","STAND"),"SES" to setOf("SPEAKER","SESSION"),
        "SPT" to setOf("PARTICIPANT","TEAM","MATCH","BRACKET"),"TRN" to setOf("ROUTE","DEPARTURE"),"LNF" to setOf("LOST_ITEM","FOUND_ITEM"),
        "AFO" to setOf("ZONE_CAPACITY","SERVICE_STATUS"),"RSC" to setOf("RESOURCE","CERTIFICATE"),"REV" to setOf("SURVEY","SURVEY_RESPONSE"),
        "GST" to setOf("SEATING_AREA"),"ADM" to setOf("GLOBAL_SETTING")
    )
    @Transactional fun create(userId:UUID,eventId:UUID,moduleRaw:String,typeRaw:String,r:ModuleRecordRequest):ModuleRecordResponse{
        val module=moduleRaw.uppercase();val type=typeRaw.uppercase();if("$module:$type" in guestCreatable)eventService.accessible(userId,eventId)else eventService.owned(userId,eventId);eventService.requireModule(eventId,module);validateType(module,type);validateWindow(r.startsAt,r.endsAt);if(r.capacity!=null&&r.capacity<0)throw BadRequestException("Capacidad inválida")
        val initialStatus=if(module=="GAL"&&type=="PHOTO"&&!isManager(userId,eventId))"PENDING" else r.status.uppercase()
        return response(records.save(ModuleRecord(eventId=eventId,moduleCode=module,recordType=type,ownerUserId=userId,parentRecordId=r.parentRecordId,status=initialStatus,title=r.title,payload=mapper.writeValueAsString(r.payload),capacity=r.capacity,startsAt=r.startsAt,endsAt=r.endsAt)))
    }
    @Transactional(readOnly=true) fun list(userId:UUID,eventId:UUID,moduleRaw:String,typeRaw:String):List<ModuleRecordResponse>{val module=moduleRaw.uppercase();val type=typeRaw.uppercase();eventService.accessible(userId,eventId);eventService.requireModule(eventId,module);validateType(module,type);val manager=isManager(userId,eventId);return records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,module,type).filter{it.status!="ARCHIVED"}.filter{manager||it.status!="PENDING"||it.ownerUserId==userId}.filter{manager||module!="ADM"}.filter{manager||module!="RSC"||type!="CERTIFICATE"||map(it.payload)["recipientUserId"]?.toString()==userId.toString()}.map(::response)}
    @Transactional(readOnly=true) fun get(userId:UUID,eventId:UUID,id:UUID):ModuleRecordResponse{eventService.accessible(userId,eventId);return response(find(eventId,id))}
    @Transactional fun update(userId:UUID,eventId:UUID,id:UUID,r:ModuleRecordUpdate):ModuleRecordResponse{eventService.accessible(userId,eventId);val x=find(eventId,id);val manager=isManager(userId,eventId);if(!manager&&x.ownerUserId!=userId)throw ForbiddenException("No puedes modificar este registro");eventService.requireModule(eventId,x.moduleCode);validateWindow(r.startsAt?:x.startsAt,r.endsAt?:x.endsAt);r.title?.let{x.title=it};r.payload?.let{x.payload=mapper.writeValueAsString(it)};r.status?.let{if(!manager&&it.uppercase()!=x.status)throw ForbiddenException("No puedes moderar este registro");x.status=it.uppercase()};r.capacity?.let{if(it<x.currentCount)throw ConflictException("La capacidad no puede ser menor a la ocupación");x.capacity=it};r.startsAt?.let{x.startsAt=it};r.endsAt?.let{x.endsAt=it};x.updatedAt=Instant.now();return response(x)}
    @Transactional fun archive(userId:UUID,eventId:UUID,id:UUID){eventService.accessible(userId,eventId);val x=find(eventId,id);if(!isManager(userId,eventId)&&x.ownerUserId!=userId)throw ForbiddenException("No puedes retirar este registro");x.status="ARCHIVED";x.updatedAt=Instant.now()}
    @Transactional fun action(userId:UUID,eventId:UUID,id:UUID,r:ModuleActionRequest):ModuleActionResponse{
        eventService.accessible(userId,eventId);val record=records.findLocked(id)?:throw NotFoundException("Registro no encontrado");if(record.eventId!=eventId)throw NotFoundException("Registro no encontrado");eventService.requireModule(eventId,record.moduleCode)
        val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(event.status==EventStatus.FINISHED&&record.moduleCode !in setOf("GAL","RSC","REV"))throw ConflictException("Esta operación no está disponible después del evento");if(event.status==EventStatus.CANCELLED)throw ConflictException("El evento está cancelado")
        val action=r.action.uppercase();if(r.quantity<1)throw BadRequestException("Cantidad inválida");if(r.unique&&actions.existsByModuleRecordIdAndActorUserIdAndActionType(id,userId,action))throw ConflictException("La acción ya fue registrada")
        if(action in setOf("JOIN","RESERVE","ORDER","CHECK_IN","REGISTER","SAVE")){val cap=record.capacity;if(cap!=null&&record.currentCount+r.quantity>cap)throw ConflictException("No hay cupo disponible");record.currentCount+=r.quantity}
        if(action in setOf("CANCEL","LEAVE","CHECK_OUT","REMOVE")){if(record.currentCount<r.quantity)throw ConflictException("La cantidad excede el total registrado");record.currentCount-=r.quantity}
        record.updatedAt=Instant.now();val a=actions.save(ModuleAction(moduleRecordId=id,actorUserId=userId,actionType=action,payload=mapper.writeValueAsString(r.payload)));return actionResponse(a)
    }
    @Transactional(readOnly=true) fun actionList(userId:UUID,eventId:UUID,id:UUID):List<ModuleActionResponse>{eventService.accessible(userId,eventId);find(eventId,id);return actions.findAllByModuleRecordIdOrderByCreatedAt(id).map(::actionResponse)}
    private fun find(eventId:UUID,id:UUID)=records.findById(id).orElseThrow{NotFoundException("Registro no encontrado")}.also{if(it.eventId!=eventId)throw NotFoundException("Registro no encontrado")}
    private fun validateType(module:String,type:String){if(type !in (types[module]?:emptySet()))throw BadRequestException("Tipo $type no admitido para $module")}
    private fun validateWindow(start:Instant?,end:Instant?){if(start!=null&&end!=null&&!end.isAfter(start))throw BadRequestException("Período inválido")}
    private fun isManager(userId:UUID,eventId:UUID)=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false}
    @Suppress("UNCHECKED_CAST") private fun map(json:String)=mapper.readValue(json,Map::class.java) as Map<String,Any?>
    private fun response(x:ModuleRecord)=ModuleRecordResponse(requireNotNull(x.id),x.eventId,x.moduleCode,x.recordType,x.ownerUserId,x.parentRecordId,x.status,x.title,map(x.payload),x.capacity,x.currentCount,x.startsAt,x.endsAt,x.createdAt)
    private fun actionResponse(x:ModuleAction)=ModuleActionResponse(requireNotNull(x.id),x.moduleRecordId,x.actorUserId,x.actionType,map(x.payload),x.createdAt)
}

@RestController @RequestMapping("/api/events/{eventId}/module-data") class ModuleDataController(private val s:ModuleDataService){
    @PostMapping("/{module}/{type}") @ResponseStatus(HttpStatus.CREATED) fun create(a:Authentication,@PathVariable eventId:UUID,@PathVariable module:String,@PathVariable type:String,@RequestBody r:ModuleRecordRequest)=s.create(a.userId(),eventId,module,type,r)
    @GetMapping("/{module}/{type}") fun list(a:Authentication,@PathVariable eventId:UUID,@PathVariable module:String,@PathVariable type:String)=s.list(a.userId(),eventId,module,type)
    @GetMapping("/records/{id}") fun get(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.get(a.userId(),eventId,id)
    @PatchMapping("/records/{id}") fun update(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:ModuleRecordUpdate)=s.update(a.userId(),eventId,id,r)
    @DeleteMapping("/records/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun archive(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.archive(a.userId(),eventId,id)
    @PostMapping("/records/{id}/actions") @ResponseStatus(HttpStatus.CREATED) fun action(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:ModuleActionRequest)=s.action(a.userId(),eventId,id,r)
    @GetMapping("/records/{id}/actions") fun actions(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.actionList(a.userId(),eventId,id)
}
