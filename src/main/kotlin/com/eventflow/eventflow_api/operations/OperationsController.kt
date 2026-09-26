package com.eventflow.eventflow_api.operations

import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.event.EventService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

data class AgendaRequest(val title:String,val description:String?=null,val startsAt:Instant,val endsAt:Instant,val zone:String?=null,val responsible:String?=null,val capacity:Int?=null)
data class NowNextResponse(val now:AgendaItem?,val next:AgendaItem?)
data class AssistanceRequestDto(val category:String,val details:String?=null,val location:String?=null,val invitationId:UUID?=null)
data class AssistanceUpdate(val status:AssistanceStatus,val assignedUserId:UUID?=null)
data class NotificationRequest(val title:String,val body:String,val audienceType:String="ALL",val audienceValue:String?=null,val recipientUserId:UUID?=null,val channel:String="IN_APP")
data class MessageRequest(val channel:String,val body:String,val recipientUserId:UUID?=null,val reservationId:UUID?=null)

@Service class OperationsService(private val eventService:EventService,private val agenda:AgendaItemRepository,private val favorites:AgendaFavoriteRepository,private val requests:AssistanceRequestRepository,private val notifications:NotificationRepository,private val messages:ConversationMessageRepository,private val audits:AuditLogRepository,private val invitations:InvitationRepository){
    @Transactional fun addAgenda(userId:UUID,eventId:UUID,r:AgendaRequest):AgendaItem{eventService.authorized(userId,eventId,"AGENDA");eventService.requireModule(eventId,"CAL");if(r.title.isBlank()||!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Actividad inválida");return agenda.save(AgendaItem(eventId=eventId,title=r.title,description=r.description,startsAt=r.startsAt,endsAt=r.endsAt,zone=r.zone,responsible=r.responsible,capacity=r.capacity))}
    @Transactional fun updateAgenda(userId:UUID,eventId:UUID,id:UUID,r:AgendaRequest):AgendaItem{eventService.authorized(userId,eventId,"AGENDA");val i=item(eventId,id);if(!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Horario inválido");i.title=r.title;i.description=r.description;i.startsAt=r.startsAt;i.endsAt=r.endsAt;i.zone=r.zone;i.responsible=r.responsible;i.capacity=r.capacity;return i}
    @Transactional fun deleteAgenda(userId:UUID,eventId:UUID,id:UUID){eventService.authorized(userId,eventId,"AGENDA");val i=item(eventId,id);i.status="CANCELLED"}
    @Transactional(readOnly=true) fun agenda(userId:UUID,eventId:UUID):List<AgendaItem>{eventService.accessible(userId,eventId);eventService.requireModule(eventId,"CAL");return agenda.findAllByEventIdOrderByStartsAt(eventId)}
    @Transactional(readOnly=true) fun nowNext(userId:UUID,eventId:UUID):NowNextResponse{val all=agenda(userId,eventId).filter{it.status!="CANCELLED"};val now=Instant.now();return NowNextResponse(all.firstOrNull{!now.isBefore(it.startsAt)&&now.isBefore(it.endsAt)},all.firstOrNull{it.startsAt.isAfter(now)})}
    @Transactional fun favorite(userId:UUID,eventId:UUID,id:UUID,on:Boolean){eventService.accessible(userId,eventId);item(eventId,id);val key=AgendaFavoriteId(id,userId);if(on)favorites.save(AgendaFavorite(id,userId))else favorites.deleteById(key)}
    @Transactional fun assistance(userId:UUID,eventId:UUID,r:AssistanceRequestDto):AssistanceRequest{eventService.accessible(userId,eventId);eventService.requireModule(eventId,"AST");val priority=if(r.category.uppercase() in setOf("FIRST_AID","EMERGENCY","PRIMEROS_AUXILIOS"))100 else 0;return requests.save(AssistanceRequest(eventId=eventId,invitationId=r.invitationId,requesterUserId=userId,category=r.category.uppercase(),details=r.details,location=r.location,priority=priority))}
    @Transactional(readOnly=true) fun assistanceList(userId:UUID,eventId:UUID):List<AssistanceRequest>{eventService.authorized(userId,eventId,"ASSISTANCE");return requests.findAllByEventIdOrderByPriorityDescCreatedAtAsc(eventId)}
    @Transactional fun assistanceUpdate(userId:UUID,eventId:UUID,id:UUID,r:AssistanceUpdate):AssistanceRequest{eventService.authorized(userId,eventId,"ASSISTANCE");val q=requests.findById(id).orElseThrow{NotFoundException("Solicitud no encontrada")};if(q.eventId!=eventId)throw NotFoundException("Solicitud no encontrada");q.status=r.status;q.assignedUserId=r.assignedUserId?:q.assignedUserId;q.updatedAt=Instant.now();audits.save(AuditLog(actorUserId=userId,eventId=eventId,action="ASSISTANCE_${r.status}",targetType="ASSISTANCE",targetId=id));return q}
    @Transactional fun notify(userId:UUID,eventId:UUID,r:NotificationRequest):NotificationEntity{eventService.authorized(userId,eventId,"NOTIFICATIONS");eventService.requireModule(eventId,"NOT");if(r.title.isBlank()||r.body.isBlank())throw BadRequestException("Título y mensaje son obligatorios");return notifications.save(NotificationEntity(eventId=eventId,authorUserId=userId,recipientUserId=r.recipientUserId,title=r.title,body=r.body,audienceType=r.audienceType.uppercase(),audienceValue=r.audienceValue,channel=r.channel.uppercase()))}
    @Transactional(readOnly=true) fun notifications(userId:UUID,eventId:UUID):List<NotificationEntity>{eventService.accessible(userId,eventId);val inv=invitations.findAllByLinkedUserId(userId).filter{it.eventId==eventId};val manager=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false};return notifications.findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId).filter{n->manager||n.recipientUserId==userId||(n.recipientUserId==null&&when(n.audienceType){"ALL"->true;"TABLE"->inv.any{it.tableLabel==n.audienceValue};"SECTOR"->inv.any{it.sectorLabel==n.audienceValue};else->false})}}
    @Transactional fun message(userId:UUID,eventId:UUID,r:MessageRequest):ConversationMessage{eventService.accessible(userId,eventId);eventService.requireModule(eventId,"MSG");if(r.body.isBlank())throw BadRequestException("El mensaje está vacío");return messages.save(ConversationMessage(eventId=eventId,reservationId=r.reservationId,senderUserId=userId,recipientUserId=r.recipientUserId,channel=r.channel,body=r.body))}
    @Transactional(readOnly=true) fun messages(userId:UUID,eventId:UUID,channel:String):List<ConversationMessage>{eventService.accessible(userId,eventId);return messages.findAllByEventIdAndChannelOrderByCreatedAt(eventId,channel).filter{it.recipientUserId==null||it.recipientUserId==userId||it.senderUserId==userId}}
    private fun item(eventId:UUID,id:UUID)=agenda.findById(id).orElseThrow{NotFoundException("Actividad no encontrada")}.also{if(it.eventId!=eventId)throw NotFoundException("Actividad no encontrada")}
}

@RestController @RequestMapping("/api/events/{eventId}") class OperationsController(private val s:OperationsService){
    @PostMapping("/agenda") @ResponseStatus(HttpStatus.CREATED) fun add(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:AgendaRequest)=s.addAgenda(a.userId(),eventId,r)
    @GetMapping("/agenda") fun agenda(a:Authentication,@PathVariable eventId:UUID)=s.agenda(a.userId(),eventId)
    @GetMapping("/agenda/now-next") fun now(a:Authentication,@PathVariable eventId:UUID)=s.nowNext(a.userId(),eventId)
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
