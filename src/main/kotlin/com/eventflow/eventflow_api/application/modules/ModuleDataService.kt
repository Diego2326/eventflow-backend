package com.eventflow.eventflow_api.application.modules

import com.eventflow.eventflow_api.application.port.*

import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.application.event.EventService
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

@Service class ModuleDataService(private val records:ModuleRecordRepositoryPort,private val actions:ModuleActionRepositoryPort,private val invitations:InvitationRepositoryPort,private val notifications:NotificationRepositoryPort,private val events:EventRepositoryPort,private val eventService:EventService,private val mapper:JsonCodec){
    private val guestCreatable=setOf("GAL:PHOTO","NET:PROFILE","NET:MEETING","LNF:LOST_ITEM","REV:SURVEY_RESPONSE","ORD:ORDER","INT:QUESTION")
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
        if(module=="INT"&&type=="QUESTION_BOARD")validateQuestionBoard(r.payload)
        if(module=="INT"&&type=="QUESTION"){
            val board=r.parentRecordId?.let{find(eventId,it)}?:throw BadRequestException("Selecciona un tablero de preguntas")
            if(board.moduleCode!="INT"||board.recordType!="QUESTION_BOARD"||board.status!="ACTIVE")throw BadRequestException("Tablero de preguntas inválido")
            if(r.title.isNullOrBlank())throw BadRequestException("Escribe la pregunta")
        }
        if(module=="REV"&&type=="SURVEY_RESPONSE"){
            if(event.status!=EventStatus.FINISHED)throw ConflictException("La encuesta posterior abre al finalizar el evento")
            val surveyId=r.parentRecordId?:throw BadRequestException("Selecciona una encuesta")
            val survey=find(eventId,surveyId)
            if(survey.moduleCode!="REV"||survey.recordType!="SURVEY"||survey.status!="ACTIVE")throw BadRequestException("Encuesta inválida")
            if(r.payload.isEmpty())throw BadRequestException("La respuesta está vacía")
            if(records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"REV","SURVEY_RESPONSE").any{it.parentRecordId==surveyId&&it.ownerUserId==userId})throw ConflictException("Ya respondiste esta encuesta")
        }
        val payload=if(module=="ORD"&&type=="ORDER")reserveOrderItems(eventId,r.payload) else r.payload
        val initialStatus=when {
            module=="ORD"&&type=="ORDER"->"PENDING"
            module=="GAL"&&type=="PHOTO"&&!isManager(userId,eventId)->"PENDING"
            module=="INT"&&type=="QUESTION"&&!isManager(userId,eventId)->
                if(map(find(eventId,requireNotNull(r.parentRecordId)).payload)["moderationRequired"]!=false)"PENDING" else "ACTIVE"
            else->r.status.uppercase()
        }
        return response(records.save(ModuleRecord(eventId=eventId,moduleCode=module,recordType=type,ownerUserId=userId,parentRecordId=r.parentRecordId,status=initialStatus,title=r.title,payload=mapper.write(payload),capacity=r.capacity,startsAt=r.startsAt,endsAt=r.endsAt)))
    }
    @Transactional(readOnly=true) fun list(userId:UUID,eventId:UUID,moduleRaw:String,typeRaw:String):List<ModuleRecordResponse>{val module=moduleRaw.uppercase();val type=typeRaw.uppercase();eventService.accessible(userId,eventId);eventService.requireModule(eventId,module);validateType(module,type);val manager=isManager(userId,eventId)||(module=="ORD"&&type=="ORDER"&&isOrderStaff(userId,eventId));if(!manager&&!eventService.guestModuleVisible(eventId,module))return emptyList();return records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,module,type).filter{it.status!="ARCHIVED"&&visibleTo(it,userId,manager)}.map(::response)}
    @Transactional(readOnly=true) fun get(userId:UUID,eventId:UUID,id:UUID):ModuleRecordResponse{eventService.accessible(userId,eventId);val record=find(eventId,id);eventService.requireModule(eventId,record.moduleCode);val manager=isManager(userId,eventId)||(record.moduleCode=="ORD"&&record.recordType=="ORDER"&&isOrderStaff(userId,eventId));if(!manager&&(!eventService.guestModuleVisible(eventId,record.moduleCode)||!visibleTo(record,userId,false)))throw NotFoundException("Registro no encontrado");return response(record)}
    @Transactional fun update(userId:UUID,eventId:UUID,id:UUID,r:ModuleRecordUpdate):ModuleRecordResponse{eventService.accessible(userId,eventId);val x=find(eventId,id);val manager=isManager(userId,eventId);val orderStaff=x.moduleCode=="ORD"&&x.recordType=="ORDER"&&isOrderStaff(userId,eventId);if(!manager&&!orderStaff&&x.ownerUserId!=userId)throw ForbiddenException("No puedes modificar este registro");eventService.requireModule(eventId,x.moduleCode);if(!manager&&!orderStaff&&!eventService.guestModuleVisible(eventId,x.moduleCode))throw ForbiddenException("El módulo no está disponible para invitados");if(!manager&&x.moduleCode=="GAL"&&x.status=="ACTIVE"&&(r.title!=null||r.payload!=null))throw ConflictException("La foto aprobada requiere nueva moderación");if(!manager&&x.moduleCode=="REV"&&x.recordType=="SURVEY_RESPONSE")throw ForbiddenException("La respuesta enviada no se puede modificar");if(!manager&&!orderStaff&&(r.capacity!=null||r.startsAt!=null||r.endsAt!=null))throw ForbiddenException("No puedes cambiar capacidad u horario");validateWindow(r.startsAt?:x.startsAt,r.endsAt?:x.endsAt);if(x.moduleCode=="ORD"&&x.recordType=="ORDER"){
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
        if(x.moduleCode=="INT"&&x.recordType=="QUESTION_BOARD"&&r.payload!=null)validateQuestionBoard(r.payload)
        if(x.moduleCode=="INT"&&x.recordType=="POLL"){
            val votes=actions.findAllByModuleRecordIdOrderByCreatedAt(id).count{it.actionType=="VOTE"}
            r.payload?.let{newPayload->
                validatePoll(newPayload)
                if(votes>0&&(pollOptions(newPayload)!=pollOptions(map(x.payload))||newPayload["allowMultiple"]!=map(x.payload)["allowMultiple"]))throw ConflictException("No puedes cambiar las reglas con votos registrados")
            }
            r.status?.uppercase()?.let{if(it !in setOf("ACTIVE","CLOSED")||x.status=="CLOSED"&&it!="CLOSED")throw ConflictException("Transición de encuesta no permitida")}
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
    @Transactional fun archive(userId:UUID,eventId:UUID,id:UUID){eventService.accessible(userId,eventId);val x=find(eventId,id);if(!isManager(userId,eventId)&&x.ownerUserId!=userId)throw ForbiddenException("No puedes retirar este registro");if(x.moduleCode=="REV"&&x.recordType=="SURVEY_RESPONSE"&&!isManager(userId,eventId))throw ForbiddenException("La respuesta enviada no se puede retirar");if(x.moduleCode=="GST"&&x.recordType=="SEATING_AREA"&&invitations.findAllByEventId(eventId).any{it.revokedAt==null&&it.tableLabel.equals(x.title,true)})throw ConflictException("La mesa tiene invitados asignados");if(x.moduleCode=="ORD"&&x.recordType=="ORDER"&&x.status !in setOf("CANCELLED","DELIVERED"))throw ConflictException("El pedido sigue activo");x.status="ARCHIVED";x.updatedAt=Instant.now()}
    @Transactional fun action(userId:UUID,eventId:UUID,id:UUID,r:ModuleActionRequest):ModuleActionResponse{
        eventService.accessible(userId,eventId);val record=records.findLocked(id)?:throw NotFoundException("Registro no encontrado");if(record.eventId!=eventId)throw NotFoundException("Registro no encontrado");eventService.requireModule(eventId,record.moduleCode);val manager=isManager(userId,eventId);if(!manager&&!eventService.guestModuleVisible(eventId,record.moduleCode)||!visibleTo(record,userId,manager)||record.status!="ACTIVE")throw ConflictException("El registro no está disponible")
        val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(event.status==EventStatus.FINISHED&&record.moduleCode !in setOf("GAL","RSC","REV"))throw ConflictException("Esta operación no está disponible después del evento");if(event.status==EventStatus.CANCELLED)throw ConflictException("El evento está cancelado")
        val action=r.action.uppercase()
        if(r.quantity<1)throw BadRequestException("Cantidad inválida")
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
        val multiplePollVote=record.moduleCode=="INT"&&record.recordType=="POLL"&&action=="VOTE"&&map(record.payload)["allowMultiple"]==true
        val unique=if(action in setOf("CANCEL","LEAVE","CHECK_OUT","REMOVE")||multiplePollVote)false else r.unique||mandatoryUnique
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
        if(record.status!="ACTIVE"&&record.ownerUserId!=userId&&!(record.moduleCode=="INT"&&record.recordType=="POLL"&&record.status=="CLOSED"))return false
        if(record.moduleCode=="MAP"&&map(record.payload)["visible"]==false)return false
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
