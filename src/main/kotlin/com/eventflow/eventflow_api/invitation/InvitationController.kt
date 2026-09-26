package com.eventflow.eventflow_api.invitation

import com.eventflow.eventflow_api.auth.service.AuthService
import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.event.EventService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.ObjectMapper
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

data class CreateInvitationRequest(val guestName:String,val guestEmail:String?=null,val allowedCapacity:Int=1,val expiresAt:Instant?=null,val table:String?=null,val seat:String?=null,val sector:String?=null)
data class InvitationResponse(val id:UUID,val eventId:UUID,val guestName:String,val guestEmail:String?,val status:InvitationStatus,val allowedCapacity:Int,val companions:List<String>,val table:String?,val seat:String?,val sector:String?,val token:String?=null,val checkedIn:Int=0)
data class RsvpRequest(val accepted:Boolean,val companions:List<String> = emptyList())
data class CheckRequest(val quantity:Int=1)
data class GuestEventResponse(val id:UUID,val name:String,val type:String,val description:String?,val startsAt:Instant,val endsAt:Instant?,val timezone:String,val location:String?,val status:EventStatus)
data class GuestModuleResponse(val code:String,val name:String,val category:String,val description:String,val order:Int,val featured:Boolean,val configuration:Map<String,Any?>)
data class GuestMapPointResponse(val id:UUID,val type:String,val title:String?,val payload:Map<String,Any?>)
data class GuestExperienceResponse(val invitation:InvitationResponse,val event:GuestEventResponse,val modules:List<GuestModuleResponse>,val agenda:List<AgendaItem>,val now:AgendaItem?,val next:AgendaItem?,val notifications:List<NotificationEntity>,val mapPoints:List<GuestMapPointResponse>)
data class GuestAssistanceRequest(val category:String,val details:String?=null,val location:String?=null)
data class LinkInvitationsRequest(val tokens:List<String> = emptyList())

@Service class InvitationService(private val repo:InvitationRepository,private val logs:GuestAccessLogRepository,private val events:EventRepository,private val eventModules:EventModuleRepository,private val catalog:ModuleCatalogRepository,private val agenda:AgendaItemRepository,private val notifications:NotificationRepository,private val records:ModuleRecordRepository,private val assistance:AssistanceRequestRepository,private val eventService:EventService,private val auth:AuthService,private val mapper:ObjectMapper){
    private val random=SecureRandom()
    @Transactional fun create(userId:UUID,eventId:UUID,r:CreateInvitationRequest):InvitationResponse{eventService.owned(userId,eventId);eventService.requireModule(eventId,"INV");if(r.guestName.isBlank()||r.allowedCapacity<1)throw BadRequestException("Nombre y cupo válidos son obligatorios");val raw=ByteArray(32).also(random::nextBytes).let{Base64.getUrlEncoder().withoutPadding().encodeToString(it)};val i=repo.save(Invitation(eventId=eventId,guestName=r.guestName.trim(),guestEmail=r.guestEmail?.lowercase(),tokenHash=auth.hash(raw),tokenExpiresAt=r.expiresAt?:Instant.now().plus(365,ChronoUnit.DAYS),allowedCapacity=r.allowedCapacity,tableLabel=r.table,seatLabel=r.seat,sectorLabel=r.sector));return response(i,raw)}
    @Transactional(readOnly=true) fun list(userId:UUID,eventId:UUID):List<InvitationResponse>{eventService.owned(userId,eventId);return repo.findAllByEventId(eventId).map{response(it)}}
    @Transactional(readOnly=true) fun access(raw:String):InvitationResponse=response(valid(raw))
    @Transactional(readOnly=true) fun experience(raw:String):GuestExperienceResponse=experience(valid(raw))
    @Transactional(readOnly=true) fun mine(userId:UUID):List<GuestExperienceResponse> = repo.findAllByLinkedUserId(userId).map(::experience)
    @Transactional(readOnly=true) fun linkedExperience(userId:UUID,id:UUID):GuestExperienceResponse=experience(linked(userId,id))
    private fun experience(invitation:Invitation):GuestExperienceResponse{
        val event=events.findById(invitation.eventId).orElseThrow{NotFoundException("Evento no encontrado")}
        if(event.status==EventStatus.DRAFT)throw ForbiddenException("El evento todavía no está publicado")
        val modules=eventModules.findAllByEventIdOrderByDisplayOrder(invitation.eventId).filter{it.enabled}.mapNotNull{em->catalog.findById(em.moduleCode).orElse(null)?.takeIf{it.globallyEnabled}?.let{c->GuestModuleResponse(c.code,c.name,c.category,c.description,em.displayOrder,em.featured,map(em.configuration))}}
        val enabled=modules.map{it.code}.toSet();val agendaItems=if("CAL" in enabled)agenda.findAllByEventIdOrderByStartsAt(invitation.eventId).filter{it.status!="CANCELLED"}else emptyList();val current=Instant.now()
        val visibleNotifications=if("NOT" in enabled)notifications.findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(invitation.eventId).filter{n->n.recipientUserId==invitation.linkedUserId||(n.recipientUserId==null&&when(n.audienceType){"ALL"->true;"TABLE"->invitation.tableLabel==n.audienceValue;"SECTOR"->invitation.sectorLabel==n.audienceValue;else->false})}else emptyList()
        val mapPoints=if("MAP" in enabled)listOf("ZONE","POINT").flatMap{type->records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(invitation.eventId,"MAP",type)}.filter{it.status=="ACTIVE"}.map{GuestMapPointResponse(requireNotNull(it.id),it.recordType,it.title,map(it.payload))}else emptyList()
        return GuestExperienceResponse(response(invitation),GuestEventResponse(requireNotNull(event.id),event.name,event.type,event.description,event.startsAt,event.endsAt,event.timezone,event.location,event.status),modules,agendaItems,agendaItems.firstOrNull{!current.isBefore(it.startsAt)&&current.isBefore(it.endsAt)},agendaItems.firstOrNull{it.startsAt.isAfter(current)},visibleNotifications,mapPoints)
    }
    @Transactional fun rsvp(raw:String,r:RsvpRequest):InvitationResponse{val i=valid(raw);if(r.companions.size+1>i.allowedCapacity)throw ConflictException("El cupo autorizado es ${i.allowedCapacity}");i.status=if(r.accepted)InvitationStatus.ACCEPTED else InvitationStatus.DECLINED;i.companions=mapper.writeValueAsString(r.companions);return response(i)}
    @Transactional fun linkedRsvp(userId:UUID,id:UUID,r:RsvpRequest):InvitationResponse{val i=linked(userId,id);if(r.companions.size+1>i.allowedCapacity)throw ConflictException("El cupo autorizado es ${i.allowedCapacity}");i.status=if(r.accepted)InvitationStatus.ACCEPTED else InvitationStatus.DECLINED;i.companions=mapper.writeValueAsString(r.companions);return response(i)}
    @Transactional fun assistance(raw:String,r:GuestAssistanceRequest):AssistanceRequest{val i=valid(raw);eventService.requireModule(i.eventId,"AST");if(r.category.isBlank())throw BadRequestException("Selecciona una categoría");val category=r.category.trim().uppercase();val priority=if(category in setOf("FIRST_AID","EMERGENCY","PRIMEROS_AUXILIOS"))100 else 0;return assistance.save(AssistanceRequest(eventId=i.eventId,invitationId=requireNotNull(i.id),requesterUserId=i.linkedUserId,category=category,details=r.details?.trim(),location=r.location?.trim(),priority=priority))}
    @Transactional fun linkedAssistance(userId:UUID,id:UUID,r:GuestAssistanceRequest):AssistanceRequest{val i=linked(userId,id);eventService.requireModule(i.eventId,"AST");if(r.category.isBlank())throw BadRequestException("Selecciona una categoría");val category=r.category.trim().uppercase();val priority=if(category in setOf("FIRST_AID","EMERGENCY","PRIMEROS_AUXILIOS"))100 else 0;return assistance.save(AssistanceRequest(eventId=i.eventId,invitationId=requireNotNull(i.id),requesterUserId=userId,category=category,details=r.details?.trim(),location=r.location?.trim(),priority=priority))}
    @Transactional fun revoke(userId:UUID,eventId:UUID,id:UUID):InvitationResponse{eventService.owned(userId,eventId);val i=find(eventId,id);i.revokedAt=Instant.now();return response(i)}
    @Transactional fun regenerate(userId:UUID,eventId:UUID,id:UUID):InvitationResponse{eventService.owned(userId,eventId);val old=find(eventId,id);old.revokedAt=Instant.now();val raw=ByteArray(32).also(random::nextBytes).let{Base64.getUrlEncoder().withoutPadding().encodeToString(it)};val i=repo.save(Invitation(eventId=eventId,linkedUserId=old.linkedUserId,guestName=old.guestName,guestEmail=old.guestEmail,tokenHash=auth.hash(raw),tokenExpiresAt=old.tokenExpiresAt,status=old.status,allowedCapacity=old.allowedCapacity,companions=old.companions,tableLabel=old.tableLabel,seatLabel=old.seatLabel,sectorLabel=old.sectorLabel));return response(i,raw)}
    @Transactional fun link(userId:UUID,raw:String):InvitationResponse{val i=valid(raw);if(i.linkedUserId!=null&&i.linkedUserId!=userId)throw ConflictException("La invitación ya está vinculada");i.linkedUserId=userId;return response(i)}
    @Transactional fun linkAll(userId:UUID,tokens:List<String>):List<InvitationResponse>{
        val normalized=tokens.map(String::trim).filter(String::isNotBlank).distinct()
        if(normalized.isEmpty())throw BadRequestException("Agrega al menos una invitación")
        val invitations=normalized.map(::valid)
        if(invitations.any{it.linkedUserId!=null&&it.linkedUserId!=userId})throw ConflictException("Una de las invitaciones ya está vinculada a otra cuenta")
        invitations.forEach{it.linkedUserId=userId}
        return invitations.map{response(it)}
    }
    @Transactional fun check(userId:UUID,eventId:UUID,id:UUID,r:CheckRequest,incoming:Boolean):InvitationResponse{eventService.authorized(userId,eventId,"CHECK_IN");eventService.requireModule(eventId,"GST");val i=find(eventId,id);if(i.status!=InvitationStatus.ACCEPTED)throw ConflictException("La invitación no está confirmada");if(r.quantity<1)throw BadRequestException("Cantidad inválida");val current=count(i);val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(incoming){if(current+r.quantity>i.allowedCapacity)throw ConflictException("El ingreso supera el cupo autorizado");if(current==0&&logs.findAllByInvitationIdOrderByCreatedAt(id).any{it.action=="CHECK_OUT"}&&!event.reentryAllowed)throw ConflictException("El reingreso no está permitido");logs.save(GuestAccessLog(invitationId=id,action="CHECK_IN",quantity=r.quantity,performedBy=userId))}else{if(r.quantity>current)throw ConflictException("No puede salir más personas de las ingresadas");logs.save(GuestAccessLog(invitationId=id,action="CHECK_OUT",quantity=r.quantity,performedBy=userId))};return response(i)}
    private fun valid(raw:String):Invitation{val i=repo.findByTokenHash(auth.hash(raw))?:throw UnauthorizedException("Invitación inválida");if(i.revokedAt!=null||i.tokenExpiresAt?.isBefore(Instant.now())==true)throw UnauthorizedException("Invitación expirada o revocada");return i}
    private fun linked(userId:UUID,id:UUID):Invitation{val i=repo.findById(id).orElseThrow{NotFoundException("Invitación no encontrada")};if(i.linkedUserId!=userId)throw ForbiddenException("La invitación no pertenece a tu cuenta");if(i.revokedAt!=null||i.tokenExpiresAt?.isBefore(Instant.now())==true)throw UnauthorizedException("Invitación expirada o revocada");return i}
    private fun find(eventId:UUID,id:UUID)=repo.findLocked(id)?.also{if(it.eventId!=eventId)throw NotFoundException("Invitación no encontrada")}?:throw NotFoundException("Invitación no encontrada")
    private fun count(i:Invitation)=logs.findAllByInvitationIdOrderByCreatedAt(requireNotNull(i.id)).sumOf{if(it.action=="CHECK_IN")it.quantity else -it.quantity}.coerceAtLeast(0)
    @Suppress("UNCHECKED_CAST") private fun map(json:String)=mapper.readValue(json,Map::class.java) as Map<String,Any?>
    @Suppress("UNCHECKED_CAST") private fun response(i:Invitation,raw:String?=null)=InvitationResponse(requireNotNull(i.id),i.eventId,i.guestName,i.guestEmail,i.status,i.allowedCapacity,mapper.readValue(i.companions,List::class.java) as List<String>,i.tableLabel,i.seatLabel,i.sectorLabel,raw,count(i))
}

@RestController @RequestMapping("/api") class InvitationController(private val service:InvitationService){
    @PostMapping("/events/{eventId}/invitations") @ResponseStatus(HttpStatus.CREATED) fun create(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:CreateInvitationRequest)=service.create(a.userId(),eventId,r)
    @GetMapping("/events/{eventId}/invitations") fun list(a:Authentication,@PathVariable eventId:UUID)=service.list(a.userId(),eventId)
    @PostMapping("/events/{eventId}/invitations/{id}/revoke") fun revoke(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.revoke(a.userId(),eventId,id)
    @PostMapping("/events/{eventId}/invitations/{id}/regenerate") fun regenerate(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.regenerate(a.userId(),eventId,id)
    @PostMapping("/events/{eventId}/invitations/{id}/check-in") fun checkin(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:CheckRequest)=service.check(a.userId(),eventId,id,r,true)
    @PostMapping("/events/{eventId}/invitations/{id}/check-out") fun checkout(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:CheckRequest)=service.check(a.userId(),eventId,id,r,false)
    @GetMapping("/invitations/access/{token}") fun access(@PathVariable token:String)=service.access(token)
    @GetMapping("/invitations/access/{token}/experience") fun experience(@PathVariable token:String)=service.experience(token)
    @PostMapping("/invitations/access/{token}/rsvp") fun rsvp(@PathVariable token:String,@RequestBody r:RsvpRequest)=service.rsvp(token,r)
    @PostMapping("/invitations/access/{token}/assistance") @ResponseStatus(HttpStatus.CREATED) fun assistance(@PathVariable token:String,@RequestBody r:GuestAssistanceRequest)=service.assistance(token,r)
    @PostMapping("/invitations/access/{token}/link") fun link(a:Authentication,@PathVariable token:String)=service.link(a.userId(),token)
    @PostMapping("/invitations/link") fun linkAll(a:Authentication,@RequestBody r:LinkInvitationsRequest)=service.linkAll(a.userId(),r.tokens)
    @GetMapping("/invitations/mine") fun mine(a:Authentication)=service.mine(a.userId())
    @GetMapping("/invitations/{id}/experience") fun linkedExperience(a:Authentication,@PathVariable id:UUID)=service.linkedExperience(a.userId(),id)
    @PostMapping("/invitations/{id}/rsvp") fun linkedRsvp(a:Authentication,@PathVariable id:UUID,@RequestBody r:RsvpRequest)=service.linkedRsvp(a.userId(),id,r)
    @PostMapping("/invitations/{id}/assistance") @ResponseStatus(HttpStatus.CREATED) fun linkedAssistance(a:Authentication,@PathVariable id:UUID,@RequestBody r:GuestAssistanceRequest)=service.linkedAssistance(a.userId(),id,r)
}
