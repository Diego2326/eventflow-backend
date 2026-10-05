package com.eventflow.eventflow_api.module.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.application.port.EventRepositoryPort
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleActionRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.application.port.SurveyParticipationRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleAction
import com.eventflow.eventflow_api.module.domain.ModuleRecord
import com.eventflow.eventflow_api.module.domain.SurveyParticipation
import com.eventflow.eventflow_api.notification.application.port.NotificationRepositoryPort
import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.port.JsonCodec

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ModuleRecordRequest(val title:String?=null,val payload:Map<String,Any?> = emptyMap(),val status:String="ACTIVE",val capacity:Int?=null,val startsAt:Instant?=null,val endsAt:Instant?=null,val parentRecordId:UUID?=null)
data class ModuleRecordUpdate(val title:String?=null,val payload:Map<String,Any?>?=null,val status:String?=null,val capacity:Int?=null,val startsAt:Instant?=null,val endsAt:Instant?=null)
data class ModuleActionRequest(val action:String,val payload:Map<String,Any?> = emptyMap(),val unique:Boolean=true,val quantity:Int=1)
data class ModuleRecordResponse(val id:UUID,val eventId:UUID,val moduleCode:String,val recordType:String,val ownerUserId:UUID?,val parentRecordId:UUID?,val status:String,val title:String?,val payload:Map<String,Any?>,val capacity:Int?,val currentCount:Int,val startsAt:Instant?,val endsAt:Instant?,val createdAt:Instant)
data class ModuleActionResponse(val id:UUID,val recordId:UUID,val actorUserId:UUID?,val action:String,val payload:Map<String,Any?>,val createdAt:Instant,val quantity:Int)
data class PollOptionResult(val optionId:String,val votes:Int)
data class PollResultsResponse(val recordId:UUID,val totalVotes:Int,val options:List<PollOptionResult>)
data class MapSearchResult(val id:UUID,val type:String,val title:String?,val payload:Map<String,Any?>)
data class QueuePositionResponse(val recordId:UUID,val position:Int?,val peopleAhead:Int,val waiting:Int)

@Service class ModuleDataService(private val records:ModuleRecordRepositoryPort,private val actions:ModuleActionRepositoryPort,private val invitations:InvitationRepositoryPort,private val notifications:NotificationRepositoryPort,private val events:EventRepositoryPort,private val eventService:EventService,private val mapper:JsonCodec,
    private val surveyParticipation:SurveyParticipationRepositoryPort,private val surveyAnonymity:SurveyAnonymity){
    private val guestCreatable=setOf("GAL:PHOTO","NET:PROFILE","LNF:LOST_ITEM","REV:SURVEY_RESPONSE","ORD:ORDER","INT:QUESTION","INT:GUEST_MESSAGE","INT:SONG")
    private val types=mapOf(
        "MAP" to setOf("ZONE","POINT"),"ORD" to setOf("MENU_CATEGORY","MENU_ITEM","ORDER"),"QUE" to setOf("QUEUE"),"BKG" to setOf("ACTIVITY"),
        "INT" to setOf("POLL","QUESTION_BOARD","QUESTION","TRIVIA","DRAW","GUEST_MESSAGE","SONG"),"GAM" to setOf("PASSPORT","MILESTONE","MISSION","BADGE"),
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
        if(module=="GST"&&type=="SEATING_AREA"){
            if(r.title.isNullOrBlank())throw BadRequestException("La mesa requiere nombre")
            if(records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,module,type)
                    .any{it.status!="ARCHIVED"&&it.title.equals(r.title,true)})throw ConflictException("La mesa ya existe")
        }
        if(module=="ORD"&&type=="MENU_ITEM")validateMenuItem(r.payload)
        if(module=="MAP")validateMapPoint(r.payload)
        if(module=="ORD"&&type=="ORDER"&&(r.capacity!=null||r.startsAt!=null||r.endsAt!=null||r.parentRecordId!=null))throw BadRequestException("El pedido contiene campos no permitidos")
        if(module=="INT"&&type=="POLL")validatePoll(r.payload)
        if(module=="INT"&&type=="TRIVIA"){
            validatePoll(r.payload)
            if(r.payload["correctOptionId"]?.toString() !in pollOptions(r.payload))throw BadRequestException("Respuesta correcta inválida")
            if((r.payload["points"] as? Number)?.toInt()?.let{it<1||it>1000}==true)throw BadRequestException("Puntuación inválida")
        }
        if(module=="QUE"&&type=="QUEUE"){
            if(r.title.isNullOrBlank())throw BadRequestException("La cola requiere nombre")
            if(r.payload["notificationThreshold"]!=null&&((r.payload["notificationThreshold"] as? Number)?.toInt()?:-1) !in 0..20)throw BadRequestException("Umbral de aviso inválido")
        }
        if(module=="BKG"&&type=="ACTIVITY"||module=="TRN"&&type=="DEPARTURE"){
            if(r.title.isNullOrBlank()||r.startsAt==null||r.endsAt==null||r.capacity==null||r.capacity<1)throw BadRequestException("Se requiere nombre, horario y cupo")
        }
        if(module=="NET"&&type=="PROFILE"){
            if(r.payload["consent"]!=true)throw BadRequestException("Se requiere consentimiento")
            validateNetworkingProfile(r.payload)
            if(records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"NET","PROFILE").any{it.ownerUserId==userId&&it.status!="ARCHIVED"})throw ConflictException("Ya tienes un perfil de networking")
        }
        if(module=="NET"&&type=="MEETING")throw BadRequestException("Usa el flujo de reuniones")
        if(module=="RSC"&&type=="CERTIFICATE")throw BadRequestException("Usa el flujo de certificados")
        if(module=="LNF"&&type in setOf("LOST_ITEM","FOUND_ITEM"))validateLostFound(type,r.title,r.payload)
        if(module=="AFO")validateCapacityRecord(type,r.payload,r.capacity)
        if(module=="SPT"&&type=="MATCH"){
            val first=runCatching{UUID.fromString(r.payload["participantAId"].toString())}.getOrNull()
            val second=runCatching{UUID.fromString(r.payload["participantBId"].toString())}.getOrNull()
            val round=r.payload["round"]?.toString()?.toIntOrNull()?:1
            if(round<1||first==second&&first!=null||round==1&&(first==null||second==null))throw BadRequestException("Participantes de partido inválidos")
            listOfNotNull(first,second).forEach{participantId->
                val participant=find(eventId,participantId)
                if(participant.moduleCode!="SPT"||participant.recordType !in setOf("PARTICIPANT","TEAM")||participant.status!="ACTIVE")
                    throw BadRequestException("Participante de partido inválido")
            }
        }
        if(module=="GAM"&&type=="MISSION"){
            val requirements=r.payload["requirements"] as? List<*>?:throw BadRequestException("La misión requiere condiciones")
            if(requirements.isEmpty()||requirements.size>20)throw BadRequestException("Condiciones de misión inválidas")
            requirements.forEach{raw->
                val requirement=raw as? Map<*,*>?:throw BadRequestException("Condición inválida")
                val recordId=runCatching{UUID.fromString(requirement["recordId"].toString())}.getOrNull()
                    ?:throw BadRequestException("Registro de condición inválido")
                if(find(eventId,recordId).status!="ACTIVE"||requirement["action"]?.toString() !in setOf("CHECK_IN","VOTE","ANSWER","SAVE","RESERVE","JOIN"))
                    throw BadRequestException("Condición inválida")
            }
        }
        if(module=="GAM"&&type=="BADGE"){
            val missionId=runCatching{UUID.fromString(r.payload["missionId"].toString())}.getOrNull()
                ?:throw BadRequestException("La insignia requiere una misión")
            val mission=find(eventId,missionId)
            if(mission.moduleCode!="GAM"||mission.recordType!="MISSION")throw BadRequestException("Misión de insignia inválida")
        }
        if(module=="INT"&&type=="QUESTION_BOARD")validateQuestionBoard(r.payload)
        if(module=="INT"&&type=="QUESTION"){
            val board=r.parentRecordId?.let{find(eventId,it)}?:throw BadRequestException("Selecciona un tablero de preguntas")
            if(board.moduleCode!="INT"||board.recordType!="QUESTION_BOARD"||board.status!="ACTIVE")throw BadRequestException("Tablero de preguntas inválido")
            if(r.title.isNullOrBlank())throw BadRequestException("Escribe la pregunta")
        }
        if(module=="REV"&&type=="SURVEY"&&r.payload["anonymous"]!=null&&r.payload["anonymous"] !is Boolean)
            throw BadRequestException("Configuración de anonimato inválida")
        if(module=="INT"&&type=="GUEST_MESSAGE"&&r.title.isNullOrBlank())throw BadRequestException("Escribe un mensaje")
        if(module=="INT"&&type=="SONG"){
            if(r.title.isNullOrBlank())throw BadRequestException("Indica la canción")
            val max=(eventService.modules(userId,eventId).first{it.code=="INT"}.configuration["maxSongRequestsPerUser"] as? Number)?.toInt()?:3
            if(max !in 1..20)throw BadRequestException("Límite de solicitudes inválido")
            if(!isManager(userId,eventId)&&records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"INT","SONG").count{it.ownerUserId==userId&&it.status!="ARCHIVED"}>=max)throw ConflictException("Alcanzaste el límite de canciones")
        }
        if(module=="REV"&&type=="SURVEY_RESPONSE"){
            if(event.status!=EventStatus.FINISHED)throw ConflictException("La encuesta posterior abre al finalizar el evento")
            val surveyId=r.parentRecordId?:throw BadRequestException("Selecciona una encuesta")
            val survey=records.findLocked(surveyId)?:throw NotFoundException("Encuesta no encontrada")
            if(survey.moduleCode!="REV"||survey.recordType!="SURVEY"||survey.status!="ACTIVE")throw BadRequestException("Encuesta inválida")
            if(survey.eventId!=eventId)throw NotFoundException("Encuesta no encontrada")
            if(r.payload.isEmpty())throw BadRequestException("La respuesta está vacía")
            if(map(survey.payload)["anonymous"]==true){
                if(surveyParticipation.existsById(surveyAnonymity.participantHash(surveyId,userId)))throw ConflictException("Ya respondiste esta encuesta")
            }else if(records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"REV","SURVEY_RESPONSE").any{it.parentRecordId==surveyId&&it.ownerUserId==userId})throw ConflictException("Ya respondiste esta encuesta")
        }
        val payload=if(module=="ORD"&&type=="ORDER")reserveOrderItems(eventId,r.payload) else r.payload
        val initialStatus=when {
            module=="ORD"&&type=="ORDER"->"PENDING"
            module=="LNF"&&type=="LOST_ITEM"->"OPEN"
            module=="LNF"&&type=="FOUND_ITEM"->"ACTIVE"
            module=="GAL"&&type=="PHOTO"&&!isManager(userId,eventId)->"PENDING"
            module=="INT"&&type=="GUEST_MESSAGE"&&!isManager(userId,eventId)&&eventService.modules(userId,eventId).first{it.code=="INT"}.configuration["guestbookModeration"]!=false->"PENDING"
            module=="INT"&&type=="QUESTION"&&!isManager(userId,eventId)->
                if(map(find(eventId,requireNotNull(r.parentRecordId)).payload)["moderationRequired"]!=false)"PENDING" else "ACTIVE"
            else->r.status.uppercase()
        }
        val anonymousResponse=module=="REV"&&type=="SURVEY_RESPONSE"&&map(find(eventId,requireNotNull(r.parentRecordId)).payload)["anonymous"]==true
        val saved=records.save(ModuleRecord(eventId=eventId,moduleCode=module,recordType=type,ownerUserId=if(anonymousResponse)null else userId,parentRecordId=r.parentRecordId,status=initialStatus,title=r.title,payload=mapper.write(payload),capacity=r.capacity,startsAt=r.startsAt,endsAt=r.endsAt))
        if(anonymousResponse)surveyParticipation.save(SurveyParticipation(surveyAnonymity.participantHash(requireNotNull(r.parentRecordId),userId),r.parentRecordId,requireNotNull(saved.id)))
        return response(saved)
    }
    @Transactional(readOnly=true) fun list(userId:UUID,eventId:UUID,moduleRaw:String,typeRaw:String):List<ModuleRecordResponse>{val module=moduleRaw.uppercase();val type=typeRaw.uppercase();eventService.accessible(userId,eventId);eventService.requireModule(eventId,module);validateType(module,type);val manager=isManager(userId,eventId)||(module=="ORD"&&type=="ORDER"&&isOrderStaff(userId,eventId));if(!manager&&!eventService.guestModuleVisible(eventId,module))return emptyList();return records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,module,type).filter{it.status!="ARCHIVED"&&visibleTo(it,userId,manager)}.map(::response)}
    @Transactional(readOnly=true) fun get(userId:UUID,eventId:UUID,id:UUID):ModuleRecordResponse{eventService.accessible(userId,eventId);val record=find(eventId,id);eventService.requireModule(eventId,record.moduleCode);val manager=isManager(userId,eventId)||(record.moduleCode=="ORD"&&record.recordType=="ORDER"&&isOrderStaff(userId,eventId));if(!manager&&(!eventService.guestModuleVisible(eventId,record.moduleCode)||!visibleTo(record,userId,false)))throw NotFoundException("Registro no encontrado");return response(record)}
    @Transactional fun update(userId:UUID,eventId:UUID,id:UUID,r:ModuleRecordUpdate):ModuleRecordResponse{eventService.accessible(userId,eventId);val x=find(eventId,id);val manager=isManager(userId,eventId);val orderStaff=x.moduleCode=="ORD"&&x.recordType=="ORDER"&&isOrderStaff(userId,eventId);if(!manager&&!orderStaff&&x.ownerUserId!=userId)throw ForbiddenException("No puedes modificar este registro");eventService.requireModule(eventId,x.moduleCode);if(!manager&&!orderStaff&&!eventService.guestModuleVisible(eventId,x.moduleCode))throw ForbiddenException("El módulo no está disponible para invitados");if(!manager&&x.moduleCode=="GAL"&&x.status=="ACTIVE"&&(r.title!=null||r.payload!=null))throw ConflictException("La foto aprobada requiere nueva moderación");if(!manager&&x.moduleCode=="REV"&&x.recordType=="SURVEY_RESPONSE")throw ForbiddenException("La respuesta enviada no se puede modificar");if(!manager&&!orderStaff&&(r.capacity!=null||r.startsAt!=null||r.endsAt!=null))throw ForbiddenException("No puedes cambiar capacidad u horario");validateWindow(r.startsAt?:x.startsAt,r.endsAt?:x.endsAt);if(x.moduleCode=="ORD"&&x.recordType=="ORDER"){
        if(x.moduleCode=="NET"&&x.recordType=="MEETING")throw BadRequestException("Usa el flujo de reuniones")
        if(x.moduleCode=="RSC"&&x.recordType=="CERTIFICATE")throw BadRequestException("Retira y vuelve a publicar el certificado")
        if(x.moduleCode=="SPT"&&x.recordType=="MATCH"&&r.payload!=null)throw BadRequestException("Usa el flujo de resultados")
        if(x.moduleCode=="GAM"&&x.recordType in setOf("MISSION","BADGE")&&r.payload!=null)
            throw BadRequestException("Las condiciones publicadas no se pueden cambiar")
        if(x.moduleCode=="REV"&&x.recordType=="SURVEY"&&r.payload!=null){
            val previous=map(x.payload)["anonymous"]==true
            if(r.payload["anonymous"]!=null&&r.payload["anonymous"] !is Boolean)throw BadRequestException("Configuración de anonimato inválida")
            if((r.payload["anonymous"]==true)!=previous&&records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"REV","SURVEY_RESPONSE").any{it.parentRecordId==id})
                throw ConflictException("No puedes cambiar el anonimato después de recibir respuestas")
        }
            if(r.payload!=null||r.title!=null||r.capacity!=null||r.startsAt!=null||r.endsAt!=null)throw ForbiddenException("El detalle del pedido no se puede modificar")
            r.status?.uppercase()?.let { target ->
                val allowed=mapOf("PENDING" to setOf("ACCEPTED","CANCELLED"),"ACCEPTED" to setOf("PREPARING","CANCELLED"),"PREPARING" to setOf("READY","CANCELLED"),"READY" to setOf("DELIVERED"))
                if(!manager&&!orderStaff&&(target!="CANCELLED"||x.status!="PENDING"))throw ForbiddenException("Solo el personal puede avanzar pedidos")
                if(target !in (allowed[x.status]?:emptySet()))throw ConflictException("Transición de pedido no permitida")
                if(target=="CANCELLED")releaseOrderItems(eventId,map(x.payload))
            }
        };if(x.moduleCode=="ORD"&&x.recordType=="MENU_ITEM"&&r.payload!=null)validateMenuItem(r.payload)
        if(x.moduleCode=="MAP"&&r.payload!=null)validateMapPoint(r.payload)
        if(x.moduleCode=="INT"&&x.recordType=="QUESTION"&&!manager){
            if(x.status!="PENDING"||r.status!=null)throw ForbiddenException("La pregunta ya fue enviada a moderación")
        }
        if(x.moduleCode=="INT"&&x.recordType=="QUESTION"&&r.status!=null&&r.status.uppercase() !in setOf("ACTIVE","REJECTED"))throw ConflictException("Estado de pregunta inválido")
        if(x.moduleCode=="INT"&&x.recordType=="GUEST_MESSAGE"&&r.status!=null&&r.status.uppercase() !in setOf("ACTIVE","REJECTED"))throw ConflictException("Estado de mensaje inválido")
        if(x.moduleCode=="INT"&&x.recordType=="QUESTION_BOARD"&&r.payload!=null)validateQuestionBoard(r.payload)
        if(x.moduleCode=="INT"&&x.recordType=="POLL"){
            val votes=actions.findAllByModuleRecordIdOrderByCreatedAt(id).count{it.actionType=="VOTE"}
            r.payload?.let{newPayload->
                validatePoll(newPayload)
                if(votes>0&&(pollOptions(newPayload)!=pollOptions(map(x.payload))||newPayload["allowMultiple"]!=map(x.payload)["allowMultiple"]))throw ConflictException("No puedes cambiar las reglas con votos registrados")
            }
            r.status?.uppercase()?.let{if(it !in setOf("ACTIVE","CLOSED")||x.status=="CLOSED"&&it!="CLOSED")throw ConflictException("Transición de encuesta no permitida")}
        }
        if(x.moduleCode in setOf("QUE","BKG","TRN","SES")&&r.status!=null&&r.status.uppercase() !in setOf("ACTIVE","CLOSED"))throw ConflictException("Estado no permitido")
        if(x.moduleCode=="NET"&&x.recordType=="PROFILE"&&r.payload!=null){
            if(r.payload["consent"]!=true)throw BadRequestException("Se requiere consentimiento")
            validateNetworkingProfile(r.payload)
        }
        if(x.moduleCode=="BKG"&&x.recordType=="ACTIVITY"&&r.payload!=null&&r.payload["cancelBeforeMinutes"]!=null&&((r.payload["cancelBeforeMinutes"] as? Number)?.toLong()?:-1L) !in 0..10080)throw BadRequestException("Plazo de cancelación inválido")
        if(x.moduleCode=="AFO")validateCapacityRecord(x.recordType,r.payload?:map(x.payload),r.capacity?:x.capacity)
        if(x.moduleCode=="LNF"){
            validateLostFound(x.recordType,r.title?:x.title,r.payload?:map(x.payload))
            r.status?.uppercase()?.let { target ->
                val allowed=if(x.recordType=="LOST_ITEM")setOf("OPEN","RESOLVED") else setOf("ACTIVE","DELIVERED")
                if(target !in allowed)throw ConflictException("Estado de objeto inválido")
                if(!manager&&target!=x.status)throw ForbiddenException("Solo el personal puede resolver reportes")
            }
        }
        if(x.moduleCode=="GST"&&x.recordType=="SEATING_AREA"){
            if(r.title!=null&&r.title.isBlank())throw BadRequestException("La mesa requiere nombre")
            val assigned=invitations.findAllByEventId(eventId).filter{it.revokedAt==null&&it.tableLabel.equals(x.title,true)}.sumOf{it.allowedCapacity}
            if(r.capacity!=null&&r.capacity<assigned)throw ConflictException("La capacidad no puede ser menor a los invitados asignados")
            if(r.title!=null&&!r.title.equals(x.title,true)&&assigned>0)throw ConflictException("No puedes renombrar una mesa con invitados asignados")
            if(r.title!=null&&!r.title.equals(x.title,true)&&records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"GST","SEATING_AREA").any{it.id!=id&&it.status!="ARCHIVED"&&it.title.equals(r.title,true)})throw ConflictException("La mesa ya existe")
        };r.title?.let{x.title=it};r.payload?.let{x.payload=mapper.write(it)};r.status?.let{if(!manager&&!orderStaff&&x.recordType!="ORDER"&&it.uppercase()!=x.status)throw ForbiddenException("No puedes moderar este registro");x.status=it.uppercase()};r.capacity?.let{if(it<x.currentCount||it<0)throw ConflictException("Capacidad inválida");x.capacity=it};r.startsAt?.let{x.startsAt=it};r.endsAt?.let{x.endsAt=it};x.updatedAt=Instant.now()
        if(x.moduleCode=="ORD"&&x.recordType=="ORDER"&&r.status!=null&&x.ownerUserId!=null&&moduleEnabled(eventId,"NOT"))
            notifications.save(NotificationEntity(eventId=eventId,authorUserId=userId,recipientUserId=x.ownerUserId,title="Pedido ${orderStatusLabel(x.status)}",body="Tu pedido cambió a ${orderStatusLabel(x.status)}",audienceType="USER"))
        return response(x)}
    @Transactional fun archive(userId:UUID,eventId:UUID,id:UUID){eventService.accessible(userId,eventId);val x=find(eventId,id);if(!isManager(userId,eventId)&&x.ownerUserId!=userId)throw ForbiddenException("No puedes retirar este registro");if(x.moduleCode=="NET"&&x.recordType=="MEETING")throw BadRequestException("Usa el flujo de reuniones");if(x.moduleCode=="REV"&&x.recordType=="SURVEY_RESPONSE"&&!isManager(userId,eventId))throw ForbiddenException("La respuesta enviada no se puede retirar");if(x.moduleCode=="GST"&&x.recordType=="SEATING_AREA"&&invitations.findAllByEventId(eventId).any{it.revokedAt==null&&it.tableLabel.equals(x.title,true)})throw ConflictException("La mesa tiene invitados asignados");if(x.moduleCode=="ORD"&&x.recordType=="ORDER"&&x.status !in setOf("CANCELLED","DELIVERED"))throw ConflictException("El pedido sigue activo");x.status="ARCHIVED";x.updatedAt=Instant.now()}
    @Transactional fun action(userId:UUID,eventId:UUID,id:UUID,r:ModuleActionRequest):ModuleActionResponse{
        eventService.accessible(userId,eventId);val record=records.findLocked(id)?:throw NotFoundException("Registro no encontrado");if(record.eventId!=eventId)throw NotFoundException("Registro no encontrado");eventService.requireModule(eventId,record.moduleCode);val manager=isManager(userId,eventId);if(!manager&&!eventService.guestModuleVisible(eventId,record.moduleCode)||!visibleTo(record,userId,manager)||record.status!="ACTIVE")throw ConflictException("El registro no está disponible")
        val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(event.status==EventStatus.FINISHED&&record.moduleCode !in setOf("GAL","RSC","REV"))throw ConflictException("Esta operación no está disponible después del evento");if(event.status==EventStatus.CANCELLED)throw ConflictException("El evento está cancelado")
        val action=r.action.uppercase()
        if(r.quantity<1)throw BadRequestException("Cantidad inválida")
        val rules=map(record.payload)
        val now=Instant.now()
        if(record.moduleCode in setOf("BKG","TRN")){
            if(action=="RESERVE"&&record.startsAt?.isBefore(now)==true)throw ConflictException("La reserva ya cerró")
        }else if(record.startsAt?.isAfter(now)==true||record.endsAt?.isBefore(now)==true)throw ConflictException("La actividad está fuera de horario")
        val supported=when(record.moduleCode to record.recordType){
            "QUE" to "QUEUE"->setOf("JOIN","LEAVE")
            "BKG" to "ACTIVITY","TRN" to "DEPARTURE"->setOf("RESERVE","CANCEL")
            "SES" to "SESSION"->setOf("SAVE","REMOVE")
            "GAM" to "MILESTONE","GAM" to "MISSION"->emptySet()
            "EXH" to "EXHIBITOR","EXH" to "STAND","RSC" to "RESOURCE"->setOf("SAVE","REMOVE")
            "INT" to "POLL","INT" to "QUESTION","INT" to "SONG"->setOf("VOTE")
            "INT" to "TRIVIA"->setOf("ANSWER")
            else->emptySet()
        }
        if(action !in supported)throw BadRequestException("Acción no admitida para este registro")
        if(record.moduleCode in setOf("QUE","BKG","TRN","SES","GAM")&&r.quantity!=1)throw BadRequestException("La acción admite una sola persona")
        if(record.moduleCode=="BKG"&&action=="CANCEL"){
            val cutoff=(rules["cancelBeforeMinutes"] as? Number)?.toLong()?:0L
            if(record.startsAt?.minusSeconds(cutoff*60)?.isBefore(now)==true)throw ConflictException("El plazo de cancelación terminó")
        }
        if(record.moduleCode=="TRN"&&action=="RESERVE"&&rules["requiresReservation"]==false)throw ConflictException("Esta salida no requiere reserva")
        if(record.moduleCode=="INT"&&record.recordType=="TRIVIA"&&action=="ANSWER"){
            val options=pollOptions(rules)
            val option=r.payload["optionId"]?.toString()?:throw BadRequestException("Selecciona una opción")
            if(option !in options)throw BadRequestException("Opción inválida")
        }
        val history=actions.findAllByModuleRecordIdAndActorUserIdOrderByCreatedAt(id,userId)
        val reversible=mapOf("JOIN" to "LEAVE","RESERVE" to "CANCEL","CHECK_IN" to "CHECK_OUT","SAVE" to "REMOVE")
        if(action in reversible&&record.moduleCode in setOf("QUE","BKG","TRN","SES","EXH","RSC","GAM")){
            val release=reversible.getValue(action)
            if(history.count{it.actionType==action}>history.count{it.actionType==release})throw ConflictException("La acción ya está activa")
        }
        if(action !in setOf("JOIN","LEAVE","RESERVE","CANCEL","ORDER","CHECK_IN","CHECK_OUT","REGISTER")&&r.quantity!=1)throw BadRequestException("La acción admite una sola unidad")
        if(record.moduleCode=="INT"&&record.recordType=="POLL"&&action=="VOTE"){
            val data=map(record.payload)
            val option=r.payload["optionId"]?.toString()?:throw BadRequestException("Selecciona una opción")
            if(option !in pollOptions(data))throw BadRequestException("Opción inválida")
            val now=Instant.now()
            if(record.startsAt?.isAfter(now)==true||record.endsAt?.isBefore(now)==true)throw ConflictException("La encuesta está fuera de horario")
            val previous=actions.findAllByModuleRecordIdAndActorUserIdOrderByCreatedAt(id,userId).filter{it.actionType=="VOTE"}
            if(data["allowMultiple"]!=true&&previous.isNotEmpty())throw ConflictException("Ya votaste en esta encuesta")
            if(previous.any{map(it.payload)["optionId"]?.toString()==option})throw ConflictException("Ya votaste por esta opción")
        }
        if(record.moduleCode=="INT"&&record.recordType=="QUESTION"){
            if(action!="VOTE")throw BadRequestException("La pregunta solo admite votos")
            val board=record.parentRecordId?.let{find(eventId,it)}?:throw ConflictException("Tablero no disponible")
            if(board.status!="ACTIVE"||map(board.payload)["allowVotes"]!=true)throw ConflictException("Los votos están deshabilitados")
        }
        val mandatoryUnique=action in setOf("VOTE","ANSWER","SUBMIT","SAVE","CHECK_IN","REGISTER","JOIN","RESERVE")
        val multiplePollVote=record.moduleCode=="INT"&&record.recordType=="POLL"&&action=="VOTE"&&map(record.payload)["allowMultiple"]==true
        val reversibleAction=action in reversible&&record.moduleCode in setOf("QUE","BKG","TRN","SES","EXH","RSC","GAM")
        val unique=if(action in setOf("CANCEL","LEAVE","CHECK_OUT","REMOVE")||multiplePollVote||reversibleAction)false else r.unique||mandatoryUnique
        if(unique&&actions.existsByModuleRecordIdAndActorUserIdAndActionType(id,userId,action))throw ConflictException("La acción ya fue registrada")
        val occupying=setOf("JOIN","RESERVE","ORDER","CHECK_IN","REGISTER")
        val releasing=mapOf("CANCEL" to setOf("RESERVE","ORDER"),"LEAVE" to setOf("JOIN"),"CHECK_OUT" to setOf("CHECK_IN"),"REMOVE" to setOf("SAVE"))
        if(action in occupying){
            val cap=record.capacity
            if(cap!=null&&record.currentCount+r.quantity>cap)throw ConflictException("No hay cupo disponible")
            record.currentCount+=r.quantity
        }
        if(action in releasing){
            val acquired=history.filter{it.actionType in releasing.getValue(action)}.sumOf{it.quantity}
            val released=history.filter{it.actionType==action}.sumOf{it.quantity}
            if(acquired-released<r.quantity)throw ConflictException("No tienes cupo registrado para liberar")
            if(action!="REMOVE")record.currentCount-=r.quantity
        }
        record.updatedAt=Instant.now()
        val saved=actions.save(ModuleAction(moduleRecordId=id,actorUserId=userId,actionType=action,
            quantity=r.quantity,uniqueAction=unique,payload=mapper.write(if(record.moduleCode=="INT"&&record.recordType=="TRIVIA"&&action=="ANSWER")r.payload+mapOf("score" to if(r.payload["optionId"]?.toString()==rules["correctOptionId"]?.toString())((rules["points"] as? Number)?.toInt()?:1) else 0) else r.payload)))
        if(record.moduleCode=="QUE"&&action in setOf("LEAVE","JOIN")&&moduleEnabled(eventId,"NOT"))notifyNextInQueue(eventId,record)
        return actionResponse(saved)
    }
    @Transactional(readOnly=true) fun queuePosition(userId:UUID,eventId:UUID,id:UUID):QueuePositionResponse{
        eventService.requireVisibleModule(userId,eventId,"QUE")
        val record=find(eventId,id)
        if(record.moduleCode!="QUE"||record.recordType!="QUEUE")throw NotFoundException("Cola no encontrada")
        val active=linkedMapOf<UUID,Int>()
        actions.findAllByModuleRecordIdOrderByCreatedAt(id).forEach { a ->
            val actor=a.actorUserId?:return@forEach
            if(a.actionType=="JOIN")active.putIfAbsent(actor,active.size+1)
            if(a.actionType=="LEAVE")active.remove(actor)
        }
        val position=active.keys.indexOf(userId).takeIf{it>=0}?.plus(1)
        return QueuePositionResponse(id,position,(position?:0).let{if(position==null)0 else it-1},active.size)
    }
    @Transactional(readOnly=true) fun networkingSuggestions(userId:UUID,eventId:UUID):List<ModuleRecordResponse>{
        eventService.requireVisibleModule(userId,eventId,"NET")
        val profiles=records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"NET","PROFILE")
        val mine=profiles.firstOrNull{it.ownerUserId==userId&&it.status=="ACTIVE"&&map(it.payload)["visible"]==true}?:return emptyList()
        val interests=(map(mine.payload)["interests"] as? List<*>)?.map{it.toString().lowercase()}?.toSet()?:emptySet()
        return profiles.asSequence().filter{it.ownerUserId!=userId&&it.status=="ACTIVE"&&map(it.payload)["visible"]==true}
            .map{it to ((map(it.payload)["interests"] as? List<*>)?.count{interest->interest.toString().lowercase() in interests}?:0)}
            .filter{it.second>0}.sortedByDescending{it.second}.take(20).map{response(it.first)}.toList()
    }
    @Transactional(readOnly=true) fun myResources(userId:UUID,eventId:UUID):List<ModuleRecordResponse>{
        eventService.accessible(userId,eventId)
        val active=linkedSetOf<UUID>()
        actions.findAllByActorUserIdAndActionTypeInOrderByCreatedAt(userId,listOf("SAVE","REMOVE")).forEach{
            if(it.actionType=="SAVE")active.add(it.moduleRecordId) else active.remove(it.moduleRecordId)
        }
        val saved=listOf("RSC" to "RESOURCE","SES" to "SESSION","EXH" to "EXHIBITOR","EXH" to "STAND").flatMap{(module,type)->
            if(!moduleEnabled(eventId,module)||!eventService.guestModuleVisible(eventId,module))emptyList()
            else records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,module,type)
        }
        return saved.filter{it.id in active&&it.status=="ACTIVE"&&visibleTo(it,userId,false)}.map(::response)
    }
    private fun notifyNextInQueue(eventId:UUID,record:ModuleRecord){
        val threshold=(map(record.payload)["notificationThreshold"] as? Number)?.toInt()?.coerceIn(0,20)?:2
        val active=linkedMapOf<UUID,UUID>()
        actions.findAllByModuleRecordIdOrderByCreatedAt(requireNotNull(record.id)).forEach{a->a.actorUserId?.let{
            if(a.actionType=="JOIN")active[it]=requireNotNull(a.id) else if(a.actionType=="LEAVE")active.remove(it)
        }}
        active.entries.take(threshold+1).forEach{(recipient,joinId)->
            val key="QUEUE:${record.id}:$joinId"
            if(!notifications.existsByDedupeKey(key))notifications.save(NotificationEntity(eventId=eventId,recipientUserId=recipient,
                title="Tu turno se acerca",body="Hay pocas personas delante de ti en ${record.title?:"la cola"}",audienceType="USER",dedupeKey=key))
        }
    }
    @Transactional(readOnly=true) fun actionList(userId:UUID,eventId:UUID,id:UUID):List<ModuleActionResponse>{eventService.accessible(userId,eventId);val record=find(eventId,id);eventService.requireModule(eventId,record.moduleCode);val manager=isManager(userId,eventId);if(!manager&&!eventService.guestModuleVisible(eventId,record.moduleCode)||!visibleTo(record,userId,manager))throw NotFoundException("Registro no encontrado");return actions.findAllByModuleRecordIdOrderByCreatedAt(id).filter{manager||it.actorUserId==userId}.map(::actionResponse)}
    @Transactional(readOnly=true) fun pollResults(userId:UUID,eventId:UUID,id:UUID):PollResultsResponse{
        eventService.requireVisibleModule(userId,eventId,"INT")
        val poll=find(eventId,id)
        if(poll.moduleCode!="INT"||poll.recordType!="POLL"||poll.status=="ARCHIVED")throw NotFoundException("Encuesta no encontrada")
        if(!isManager(userId,eventId)&&map(poll.payload)["resultsPublished"]!=true)throw ForbiddenException("Los resultados aún no están publicados")
        val options=pollOptions(map(poll.payload))
        val counts=actions.findAllByModuleRecordIdOrderByCreatedAt(id).filter{it.actionType=="VOTE"}.mapNotNull{map(it.payload)["optionId"]?.toString()}.filter{it in options}.groupingBy{it}.eachCount()
        return PollResultsResponse(id,counts.values.sum(),options.map{PollOptionResult(it,counts[it]?:0)})
    }
    @Transactional(readOnly=true) fun mapSearch(userId:UUID,eventId:UUID,query:String):List<MapSearchResult>{
        eventService.requireVisibleModule(userId,eventId,"MAP")
        val needle=query.trim().lowercase()
        if(needle.length !in 2..100)throw BadRequestException("La búsqueda requiere entre 2 y 100 caracteres")
        val manager=isManager(userId,eventId)
        return listOf("ZONE","POINT").flatMap{records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"MAP",it)}
            .filter{it.status=="ACTIVE"&&visibleTo(it,userId,manager)}
            .filter{it.title?.lowercase()?.contains(needle)==true||map(it.payload).values.any{value->value is String&&value.lowercase().contains(needle)}}
            .take(50).map{MapSearchResult(requireNotNull(it.id),it.recordType,it.title,map(it.payload))}
    }
    @Transactional(readOnly=true) fun myMapLocation(userId:UUID,eventId:UUID):List<MapSearchResult>{
        eventService.requireVisibleModule(userId,eventId,"MAP")
        val assigned=invitations.findAllByLinkedUserId(userId).filter{it.eventId==eventId&&it.revokedAt==null}
        if(assigned.isEmpty())return emptyList()
        val table=assigned.mapNotNull{it.tableLabel}.toSet()
        val sector=assigned.mapNotNull{it.sectorLabel}.toSet()
        return listOf("ZONE","POINT").flatMap{records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"MAP",it)}
            .filter{it.status=="ACTIVE"&&visibleTo(it,userId,false)}
            .filter{map(it.payload).let{data->data["tableLabel"] in table||data["sectorLabel"] in sector}}
            .map{MapSearchResult(requireNotNull(it.id),it.recordType,it.title,map(it.payload))}
    }
    private fun find(eventId:UUID,id:UUID)=records.findById(id).orElseThrow{NotFoundException("Registro no encontrado")}.also{if(it.eventId!=eventId)throw NotFoundException("Registro no encontrado")}
    private fun validateType(module:String,type:String){if(type !in (types[module]?:emptySet()))throw BadRequestException("Tipo $type no admitido para $module")}
    private fun validateWindow(start:Instant?,end:Instant?){if(start!=null&&end!=null&&!end.isAfter(start))throw BadRequestException("Período inválido")}
    private fun isOrderStaff(userId:UUID,eventId:UUID)=try{eventService.authorized(userId,eventId,"ORDERS");true}catch(_:ForbiddenException){false}
    private fun moduleEnabled(eventId:UUID,code:String)=try{eventService.requireModule(eventId,code);true}catch(_:ConflictException){false}
    private fun orderStatusLabel(status:String)=mapOf("ACCEPTED" to "aceptado","PREPARING" to "en preparación","READY" to "listo","DELIVERED" to "entregado","CANCELLED" to "cancelado")[status]?:status.lowercase()
    private fun validateMenuItem(payload:Map<String,Any?>){
        val price=payload["price"]?.toString()?.toBigDecimalOrNull()?:throw BadRequestException("El artículo requiere precio")
        if(price<BigDecimal.ZERO||payload["available"]!=null&&payload["available"] !is Boolean)throw BadRequestException("Artículo inválido")
    }
    private fun validateMapPoint(payload:Map<String,Any?>){
        if(payload["visible"]!=null&&payload["visible"] !is Boolean)throw BadRequestException("Visibilidad de zona inválida")
        listOf("tableLabel","sectorLabel","description").forEach{key->if(payload[key]!=null&&payload[key] !is String)throw BadRequestException("$key inválido")}
    }
    private fun pollOptions(payload:Map<String,Any?>):List<String> = (payload["options"] as? List<*>)?.map{
        (it as? Map<*,*>)?.get("id")?.toString()?:throw BadRequestException("Opción de encuesta inválida")
    }?:throw BadRequestException("La encuesta requiere opciones")
    private fun validatePoll(payload:Map<String,Any?>){
        val options=pollOptions(payload)
        if(options.size !in 2..20||options.any{it.isBlank()}||options.distinct().size!=options.size)throw BadRequestException("Opciones de encuesta inválidas")
        if(payload["allowMultiple"]!=null&&payload["allowMultiple"] !is Boolean||payload["resultsPublished"]!=null&&payload["resultsPublished"] !is Boolean)throw BadRequestException("Configuración de encuesta inválida")
    }
    private fun validateQuestionBoard(payload:Map<String,Any?>){
        listOf("moderationRequired","allowVotes").forEach{key->if(payload[key]!=null&&payload[key] !is Boolean)throw BadRequestException("$key debe ser verdadero o falso")}
    }
    private fun validateNetworkingProfile(payload:Map<String,Any?>){
        if(payload.keys.any{it !in setOf("consent","visible","displayName","bio","organization","interests","contact")})throw BadRequestException("Dato de perfil no permitido")
        if(payload["visible"]!=null&&payload["visible"] !is Boolean)throw BadRequestException("Visibilidad inválida")
        if(payload["displayName"]!=null&&(payload["displayName"] !is String||(payload["displayName"] as String).length>100))throw BadRequestException("Nombre inválido")
        if(payload["bio"]!=null&&(payload["bio"] !is String||(payload["bio"] as String).length>500))throw BadRequestException("Biografía inválida")
        if(payload["organization"]!=null&&(payload["organization"] !is String||(payload["organization"] as String).length>100))throw BadRequestException("Organización inválida")
        val interests=payload["interests"]
        if(interests!=null&&(interests !is List<*>||interests.size>20||interests.any{it !is String||it.length>50}))throw BadRequestException("Intereses inválidos")
        if(payload["contact"]!=null&&(payload["contact"] !is String||(payload["contact"] as String).length>200))throw BadRequestException("Contacto inválido")
    }
    private fun validateCapacityRecord(type:String,payload:Map<String,Any?>,capacity:Int?){
        if(type=="ZONE_CAPACITY"){
            val occupied=payload["occupied"]?.toString()?.toIntOrNull()?:throw BadRequestException("Se requiere conteo de ocupación")
            if(capacity==null||capacity<1||occupied<0||occupied>capacity)throw BadRequestException("Aforo inválido")
        }
        if(type=="SERVICE_STATUS"&&payload["state"]?.toString() !in setOf("AVAILABLE","BUSY","PAUSED","CLOSED"))
            throw BadRequestException("Estado de servicio inválido")
    }
    private fun validateLostFound(type:String,title:String?,payload:Map<String,Any?>){
        if(title.isNullOrBlank()||title.length>200)throw BadRequestException("Describe el objeto")
        if(payload["category"]!=null&&(payload["category"] !is String||(payload["category"] as String).length>80))
            throw BadRequestException("Categoría inválida")
        if(type=="FOUND_ITEM"&&payload.keys.any{it in setOf("ownerName","ownerEmail","ownerPhone","contact")})
            throw BadRequestException("No publiques datos personales del propietario")
    }
    private fun orderLines(payload:Map<String,Any?>):List<Pair<UUID,Int>>{
        val items=payload["items"] as? List<*> ?:throw BadRequestException("El pedido requiere artículos")
        if(items.isEmpty()||items.size>50)throw BadRequestException("Cantidad de artículos inválida")
        val lines=items.map { raw ->
            val item=raw as? Map<*,*> ?:throw BadRequestException("Artículo inválido")
            val id=runCatching{UUID.fromString(item["itemId"]?.toString())}.getOrNull()?:throw BadRequestException("ID de artículo inválido")
            val quantity=(item["quantity"] as? Number)?.toInt()?:throw BadRequestException("Cantidad inválida")
            if(quantity !in 1..100 || (item["quantity"] as Number).toLong()!=quantity.toLong())throw BadRequestException("Cantidad inválida")
            id to quantity
        }
        if(lines.map{it.first}.distinct().size!=lines.size)throw BadRequestException("Artículo duplicado")
        return lines.sortedBy{it.first.toString()}
    }
    private fun reserveOrderItems(eventId:UUID,payload:Map<String,Any?>):Map<String,Any?>{
        var total=BigDecimal.ZERO
        val lines=orderLines(payload)
        lines.forEach { (id,quantity) ->
            val item=records.findLocked(id)?:throw NotFoundException("Artículo no encontrado")
            if(item.eventId!=eventId||item.moduleCode!="ORD"||item.recordType!="MENU_ITEM"||item.status!="ACTIVE")throw NotFoundException("Artículo no encontrado")
            val data=map(item.payload)
            validateMenuItem(data)
            if(data["available"]==false)throw ConflictException("El artículo no está disponible")
            item.capacity?.let{if(item.currentCount+quantity>it)throw ConflictException("El artículo está agotado")}
            item.currentCount+=quantity
            total+=data["price"].toString().toBigDecimal().multiply(BigDecimal(quantity))
        }
        return payload+mapOf("total" to total,"items" to lines.map{mapOf("itemId" to it.first.toString(),"quantity" to it.second)})
    }
    private fun releaseOrderItems(eventId:UUID,payload:Map<String,Any?>){
        orderLines(payload).forEach { (id,quantity) ->
            val item=records.findLocked(id)?:throw NotFoundException("Artículo no encontrado")
            if(item.eventId!=eventId||item.moduleCode!="ORD"||item.recordType!="MENU_ITEM")throw ConflictException("Artículo de pedido inválido")
            item.currentCount=(item.currentCount-quantity).coerceAtLeast(0)
        }
    }
    private fun isManager(userId:UUID,eventId:UUID)=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false}
    private fun visibleTo(record:ModuleRecord,userId:UUID,manager:Boolean):Boolean {
        if(manager)return true
        if(record.moduleCode=="ADM"||record.status=="ARCHIVED")return false
        if(record.moduleCode=="LNF"&&record.recordType=="LOST_ITEM")return record.ownerUserId==userId
        if(record.moduleCode=="NET"&&record.recordType=="MEETING")
            return record.ownerUserId==userId||map(record.payload)["participantUserId"]?.toString()==userId.toString()
        if(record.status!="ACTIVE"&&record.ownerUserId!=userId&&!(record.moduleCode=="INT"&&record.recordType=="POLL"&&record.status=="CLOSED"))return false
        if(record.moduleCode=="MAP"&&map(record.payload)["visible"]==false)return false
        if(record.moduleCode=="RSC"&&record.recordType=="CERTIFICATE")
            return map(record.payload)["recipientUserId"]?.toString()==userId.toString()
        if(record.moduleCode=="ORD"&&record.recordType=="ORDER")return record.ownerUserId==userId
        if(record.moduleCode=="LNF"&&record.recordType=="LOST_ITEM")return record.ownerUserId==userId
        if(record.moduleCode=="REV"&&record.recordType=="SURVEY_RESPONSE")return record.ownerUserId==userId
        if(record.moduleCode=="NET"&&record.recordType=="PROFILE")
            return record.ownerUserId==userId||map(record.payload)["visible"]==true
        return true
    }
    @Suppress("UNCHECKED_CAST") private fun map(json:String)=mapper.readMap(json)
    private fun response(x:ModuleRecord)=ModuleRecordResponse(requireNotNull(x.id),x.eventId,x.moduleCode,x.recordType,x.ownerUserId,x.parentRecordId,x.status,x.title,map(x.payload),x.capacity,x.currentCount,x.startsAt,x.endsAt,x.createdAt)
    private fun actionResponse(x:ModuleAction)=ModuleActionResponse(requireNotNull(x.id),x.moduleRecordId,x.actorUserId,x.actionType,map(x.payload),x.createdAt,x.quantity)
}
