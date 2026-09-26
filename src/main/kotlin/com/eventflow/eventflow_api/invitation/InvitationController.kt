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

@Service class InvitationService(private val repo:InvitationRepository,private val logs:GuestAccessLogRepository,private val events:EventRepository,private val eventService:EventService,private val auth:AuthService,private val mapper:ObjectMapper){
    private val random=SecureRandom()
    @Transactional fun create(userId:UUID,eventId:UUID,r:CreateInvitationRequest):InvitationResponse{eventService.owned(userId,eventId);eventService.requireModule(eventId,"INV");if(r.guestName.isBlank()||r.allowedCapacity<1)throw BadRequestException("Nombre y cupo válidos son obligatorios");val raw=ByteArray(32).also(random::nextBytes).let{Base64.getUrlEncoder().withoutPadding().encodeToString(it)};val i=repo.save(Invitation(eventId=eventId,guestName=r.guestName.trim(),guestEmail=r.guestEmail?.lowercase(),tokenHash=auth.hash(raw),tokenExpiresAt=r.expiresAt?:Instant.now().plus(365,ChronoUnit.DAYS),allowedCapacity=r.allowedCapacity,tableLabel=r.table,seatLabel=r.seat,sectorLabel=r.sector));return response(i,raw)}
    @Transactional(readOnly=true) fun list(userId:UUID,eventId:UUID):List<InvitationResponse>{eventService.owned(userId,eventId);return repo.findAllByEventId(eventId).map{response(it)}}
    @Transactional(readOnly=true) fun access(raw:String):InvitationResponse=response(valid(raw))
    @Transactional fun rsvp(raw:String,r:RsvpRequest):InvitationResponse{val i=valid(raw);if(r.companions.size+1>i.allowedCapacity)throw ConflictException("El cupo autorizado es ${i.allowedCapacity}");i.status=if(r.accepted)InvitationStatus.ACCEPTED else InvitationStatus.DECLINED;i.companions=mapper.writeValueAsString(r.companions);return response(i)}
    @Transactional fun revoke(userId:UUID,eventId:UUID,id:UUID):InvitationResponse{eventService.owned(userId,eventId);val i=find(eventId,id);i.revokedAt=Instant.now();return response(i)}
    @Transactional fun regenerate(userId:UUID,eventId:UUID,id:UUID):InvitationResponse{eventService.owned(userId,eventId);val old=find(eventId,id);old.revokedAt=Instant.now();val raw=ByteArray(32).also(random::nextBytes).let{Base64.getUrlEncoder().withoutPadding().encodeToString(it)};val i=repo.save(Invitation(eventId=eventId,linkedUserId=old.linkedUserId,guestName=old.guestName,guestEmail=old.guestEmail,tokenHash=auth.hash(raw),tokenExpiresAt=old.tokenExpiresAt,status=old.status,allowedCapacity=old.allowedCapacity,companions=old.companions,tableLabel=old.tableLabel,seatLabel=old.seatLabel,sectorLabel=old.sectorLabel));return response(i,raw)}
    @Transactional fun link(userId:UUID,raw:String):InvitationResponse{val i=valid(raw);if(i.linkedUserId!=null&&i.linkedUserId!=userId)throw ConflictException("La invitación ya está vinculada");i.linkedUserId=userId;return response(i)}
    @Transactional fun check(userId:UUID,eventId:UUID,id:UUID,r:CheckRequest,incoming:Boolean):InvitationResponse{eventService.authorized(userId,eventId,"CHECK_IN");eventService.requireModule(eventId,"GST");val i=find(eventId,id);if(i.status!=InvitationStatus.ACCEPTED)throw ConflictException("La invitación no está confirmada");if(r.quantity<1)throw BadRequestException("Cantidad inválida");val current=count(i);val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(incoming){if(current+r.quantity>i.allowedCapacity)throw ConflictException("El ingreso supera el cupo autorizado");if(current==0&&logs.findAllByInvitationIdOrderByCreatedAt(id).any{it.action=="CHECK_OUT"}&&!event.reentryAllowed)throw ConflictException("El reingreso no está permitido");logs.save(GuestAccessLog(invitationId=id,action="CHECK_IN",quantity=r.quantity,performedBy=userId))}else{if(r.quantity>current)throw ConflictException("No puede salir más personas de las ingresadas");logs.save(GuestAccessLog(invitationId=id,action="CHECK_OUT",quantity=r.quantity,performedBy=userId))};return response(i)}
    private fun valid(raw:String):Invitation{val i=repo.findByTokenHash(auth.hash(raw))?:throw UnauthorizedException("Invitación inválida");if(i.revokedAt!=null||i.tokenExpiresAt?.isBefore(Instant.now())==true)throw UnauthorizedException("Invitación expirada o revocada");return i}
    private fun find(eventId:UUID,id:UUID)=repo.findLocked(id)?.also{if(it.eventId!=eventId)throw NotFoundException("Invitación no encontrada")}?:throw NotFoundException("Invitación no encontrada")
    private fun count(i:Invitation)=logs.findAllByInvitationIdOrderByCreatedAt(requireNotNull(i.id)).sumOf{if(it.action=="CHECK_IN")it.quantity else -it.quantity}.coerceAtLeast(0)
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
    @PostMapping("/invitations/access/{token}/rsvp") fun rsvp(@PathVariable token:String,@RequestBody r:RsvpRequest)=service.rsvp(token,r)
    @PostMapping("/invitations/access/{token}/link") fun link(a:Authentication,@PathVariable token:String)=service.link(a.userId(),token)
}
