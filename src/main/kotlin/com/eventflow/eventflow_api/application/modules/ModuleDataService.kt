package com.eventflow.eventflow_api.application.modules

import com.eventflow.eventflow_api.application.port.*

import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.application.event.EventService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class ModuleRecordRequest(val title:String?=null,val payload:Map<String,Any?> = emptyMap(),val status:String="ACTIVE",val capacity:Int?=null,val startsAt:Instant?=null,val endsAt:Instant?=null,val parentRecordId:UUID?=null)
data class ModuleRecordUpdate(val title:String?=null,val payload:Map<String,Any?>?=null,val status:String?=null,val capacity:Int?=null,val startsAt:Instant?=null,val endsAt:Instant?=null)
data class ModuleActionRequest(val action:String,val payload:Map<String,Any?> = emptyMap(),val unique:Boolean=true,val quantity:Int=1)
data class ModuleRecordResponse(val id:UUID,val eventId:UUID,val moduleCode:String,val recordType:String,val ownerUserId:UUID?,val parentRecordId:UUID?,val status:String,val title:String?,val payload:Map<String,Any?>,val capacity:Int?,val currentCount:Int,val startsAt:Instant?,val endsAt:Instant?,val createdAt:Instant)
data class ModuleActionResponse(val id:UUID,val recordId:UUID,val actorUserId:UUID?,val action:String,val payload:Map<String,Any?>,val createdAt:Instant,val quantity:Int)

@Service class ModuleDataService(private val records:ModuleRecordRepositoryPort,private val actions:ModuleActionRepositoryPort,private val events:EventRepositoryPort,private val eventService:EventService,private val mapper:JsonCodec){
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
        if(!isManager(userId,eventId)&&!eventService.guestModuleVisible(eventId,module))throw ForbiddenException("El módulo no está disponible para invitados")
        val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")}
        if(!isManager(userId,eventId)&&(event.status==EventStatus.DRAFT||event.status==EventStatus.CANCELLED||event.status==EventStatus.FINISHED&&module !in setOf("GAL","REV")))throw ConflictException("El evento no admite esta operación")
        r.parentRecordId?.let { parentId ->
            val parent=find(eventId,parentId)
            if(parent.moduleCode!=module||parent.status=="ARCHIVED")throw BadRequestException("Registro padre inválido")
        }
        val initialStatus=if(module=="GAL"&&type=="PHOTO"&&!isManager(userId,eventId))"PENDING" else r.status.uppercase()
        return response(records.save(ModuleRecord(eventId=eventId,moduleCode=module,recordType=type,ownerUserId=userId,parentRecordId=r.parentRecordId,status=initialStatus,title=r.title,payload=mapper.write(r.payload),capacity=r.capacity,startsAt=r.startsAt,endsAt=r.endsAt)))
    }
    @Transactional(readOnly=true) fun list(userId:UUID,eventId:UUID,moduleRaw:String,typeRaw:String):List<ModuleRecordResponse>{val module=moduleRaw.uppercase();val type=typeRaw.uppercase();eventService.accessible(userId,eventId);eventService.requireModule(eventId,module);validateType(module,type);val manager=isManager(userId,eventId);if(!manager&&!eventService.guestModuleVisible(eventId,module))return emptyList();return records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,module,type).filter{it.status!="ARCHIVED"&&visibleTo(it,userId,manager)}.map(::response)}
    @Transactional(readOnly=true) fun get(userId:UUID,eventId:UUID,id:UUID):ModuleRecordResponse{eventService.accessible(userId,eventId);val record=find(eventId,id);eventService.requireModule(eventId,record.moduleCode);val manager=isManager(userId,eventId);if(!manager&&(!eventService.guestModuleVisible(eventId,record.moduleCode)||!visibleTo(record,userId,false)))throw NotFoundException("Registro no encontrado");return response(record)}
    @Transactional fun update(userId:UUID,eventId:UUID,id:UUID,r:ModuleRecordUpdate):ModuleRecordResponse{eventService.accessible(userId,eventId);val x=find(eventId,id);val manager=isManager(userId,eventId);if(!manager&&x.ownerUserId!=userId)throw ForbiddenException("No puedes modificar este registro");eventService.requireModule(eventId,x.moduleCode);if(!manager&&!eventService.guestModuleVisible(eventId,x.moduleCode))throw ForbiddenException("El módulo no está disponible para invitados");if(!manager&&x.moduleCode=="GAL"&&x.status=="ACTIVE"&&(r.title!=null||r.payload!=null))throw ConflictException("La foto aprobada requiere nueva moderación");if(!manager&&(r.capacity!=null||r.startsAt!=null||r.endsAt!=null))throw ForbiddenException("No puedes cambiar capacidad u horario");validateWindow(r.startsAt?:x.startsAt,r.endsAt?:x.endsAt);r.title?.let{x.title=it};r.payload?.let{x.payload=mapper.write(it)};r.status?.let{if(!manager&&it.uppercase()!=x.status)throw ForbiddenException("No puedes moderar este registro");x.status=it.uppercase()};r.capacity?.let{if(it<x.currentCount||it<0)throw ConflictException("Capacidad inválida");x.capacity=it};r.startsAt?.let{x.startsAt=it};r.endsAt?.let{x.endsAt=it};x.updatedAt=Instant.now();return response(x)}
    @Transactional fun archive(userId:UUID,eventId:UUID,id:UUID){eventService.accessible(userId,eventId);val x=find(eventId,id);if(!isManager(userId,eventId)&&x.ownerUserId!=userId)throw ForbiddenException("No puedes retirar este registro");x.status="ARCHIVED";x.updatedAt=Instant.now()}
    @Transactional fun action(userId:UUID,eventId:UUID,id:UUID,r:ModuleActionRequest):ModuleActionResponse{
        eventService.accessible(userId,eventId);val record=records.findLocked(id)?:throw NotFoundException("Registro no encontrado");if(record.eventId!=eventId)throw NotFoundException("Registro no encontrado");eventService.requireModule(eventId,record.moduleCode);val manager=isManager(userId,eventId);if(!manager&&!eventService.guestModuleVisible(eventId,record.moduleCode)||!visibleTo(record,userId,manager)||record.status!="ACTIVE")throw ConflictException("El registro no está disponible")
        val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(event.status==EventStatus.FINISHED&&record.moduleCode !in setOf("GAL","RSC","REV"))throw ConflictException("Esta operación no está disponible después del evento");if(event.status==EventStatus.CANCELLED)throw ConflictException("El evento está cancelado")
        val action=r.action.uppercase()
        if(r.quantity<1)throw BadRequestException("Cantidad inválida")
        val allowed=when(record.moduleCode){
            "INT"->setOf("VOTE","ANSWER","SUBMIT","REQUEST")
            "ORD"->setOf("ORDER","CANCEL")
            "GAL","RSC","EXH"->setOf("SAVE","REMOVE")
            "REV"->setOf("SUBMIT")
            "BKG","TRN"->setOf("RESERVE","CANCEL")
            "QUE"->setOf("JOIN","LEAVE")
            "SES"->setOf("CHECK_IN","CHECK_OUT","SAVE","REMOVE")
            else->setOf("JOIN","RESERVE","VOTE","SAVE","CHECK_IN","REGISTER","ORDER","CANCEL","LEAVE","CHECK_OUT","REMOVE","SUBMIT")
        }
        if(action !in allowed)throw BadRequestException("Acción no admitida para este módulo")
        val mandatoryUnique=action in setOf("VOTE","ANSWER","SUBMIT","SAVE","CHECK_IN","REGISTER","JOIN","RESERVE")
        val unique=if(action in setOf("CANCEL","LEAVE","CHECK_OUT","REMOVE"))false else r.unique||mandatoryUnique
        if(unique&&actions.existsByModuleRecordIdAndActorUserIdAndActionType(id,userId,action))throw ConflictException("La acción ya fue registrada")
        val occupying=setOf("JOIN","RESERVE","ORDER","CHECK_IN","REGISTER")
        val releasing=mapOf("CANCEL" to setOf("RESERVE","ORDER"),"LEAVE" to setOf("JOIN"),"CHECK_OUT" to setOf("CHECK_IN"),"REMOVE" to setOf("SAVE"))
        if(action in occupying){
            val cap=record.capacity
            if(cap!=null&&record.currentCount+r.quantity>cap)throw ConflictException("No hay cupo disponible")
            record.currentCount+=r.quantity
        }
        if(action in releasing){
            val history=actions.findAllByModuleRecordIdAndActorUserIdOrderByCreatedAt(id,userId)
            val acquired=history.filter{it.actionType in releasing.getValue(action)}.sumOf{it.quantity}
            val released=history.filter{it.actionType==action}.sumOf{it.quantity}
            if(acquired-released<r.quantity)throw ConflictException("No tienes cupo registrado para liberar")
            if(action!="REMOVE")record.currentCount-=r.quantity
        }
        record.updatedAt=Instant.now()
        val saved=actions.save(ModuleAction(moduleRecordId=id,actorUserId=userId,actionType=action,
            quantity=r.quantity,uniqueAction=unique,payload=mapper.write(r.payload)))
        return actionResponse(saved)
    }
    @Transactional(readOnly=true) fun actionList(userId:UUID,eventId:UUID,id:UUID):List<ModuleActionResponse>{eventService.accessible(userId,eventId);val record=find(eventId,id);eventService.requireModule(eventId,record.moduleCode);val manager=isManager(userId,eventId);if(!manager&&!eventService.guestModuleVisible(eventId,record.moduleCode)||!visibleTo(record,userId,manager))throw NotFoundException("Registro no encontrado");return actions.findAllByModuleRecordIdOrderByCreatedAt(id).filter{manager||it.actorUserId==userId}.map(::actionResponse)}
    private fun find(eventId:UUID,id:UUID)=records.findById(id).orElseThrow{NotFoundException("Registro no encontrado")}.also{if(it.eventId!=eventId)throw NotFoundException("Registro no encontrado")}
    private fun validateType(module:String,type:String){if(type !in (types[module]?:emptySet()))throw BadRequestException("Tipo $type no admitido para $module")}
    private fun validateWindow(start:Instant?,end:Instant?){if(start!=null&&end!=null&&!end.isAfter(start))throw BadRequestException("Período inválido")}
    private fun isManager(userId:UUID,eventId:UUID)=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false}
    private fun visibleTo(record:ModuleRecord,userId:UUID,manager:Boolean):Boolean {
        if(manager)return true
        if(record.moduleCode=="ADM"||record.status=="ARCHIVED")return false
        if(record.status!="ACTIVE"&&record.ownerUserId!=userId)return false
        if(record.moduleCode=="RSC"&&record.recordType=="CERTIFICATE")
            return map(record.payload)["recipientUserId"]?.toString()==userId.toString()
        if(record.moduleCode=="ORD"&&record.recordType=="ORDER")return record.ownerUserId==userId
        if(record.moduleCode=="REV"&&record.recordType=="SURVEY_RESPONSE")return record.ownerUserId==userId
        if(record.moduleCode=="NET"&&record.recordType=="PROFILE")
            return record.ownerUserId==userId||map(record.payload)["visible"]==true
        return true
    }
    @Suppress("UNCHECKED_CAST") private fun map(json:String)=mapper.readMap(json)
    private fun response(x:ModuleRecord)=ModuleRecordResponse(requireNotNull(x.id),x.eventId,x.moduleCode,x.recordType,x.ownerUserId,x.parentRecordId,x.status,x.title,map(x.payload),x.capacity,x.currentCount,x.startsAt,x.endsAt,x.createdAt)
    private fun actionResponse(x:ModuleAction)=ModuleActionResponse(requireNotNull(x.id),x.moduleRecordId,x.actorUserId,x.actionType,map(x.payload),x.createdAt,x.quantity)
}
