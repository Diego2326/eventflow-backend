package com.eventflow.eventflow_api.invitation.application

import com.eventflow.eventflow_api.agenda.application.port.AgendaItemRepositoryPort
import com.eventflow.eventflow_api.agenda.domain.AgendaItem
import com.eventflow.eventflow_api.assistance.application.port.AssistanceRequestRepositoryPort
import com.eventflow.eventflow_api.assistance.domain.AssistanceRequest
import com.eventflow.eventflow_api.auth.application.AuthService
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.application.port.EventRepositoryPort
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.application.port.GuestAccessLogRepositoryPort
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.invitation.domain.GuestAccessLog
import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.invitation.domain.InvitationStatus
import com.eventflow.eventflow_api.module.application.port.EventModuleRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleCatalogRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.domain.EventModuleId
import com.eventflow.eventflow_api.notification.application.port.NotificationRepositoryPort
import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.error.UnauthorizedException
import com.eventflow.eventflow_api.shared.application.port.JsonCodec

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

data class CreateInvitationRequest(val guestName:String,val guestEmail:String?=null,val allowedCapacity:Int=1,val expiresAt:Instant?=null,val table:String?=null,val seat:String?=null,val sector:String?=null)
data class GroupMemberStatus(val index:Int,val name:String,val inside:Boolean?)
data class InvitationResponse(val id:UUID,val eventId:UUID,val guestName:String,val guestEmail:String?,val status:InvitationStatus,val allowedCapacity:Int,val companions:List<String>,val table:String?,val seat:String?,val sector:String?,val token:String?=null,val checkedIn:Int=0,val qrPayload:String?=null,val members:List<GroupMemberStatus> = emptyList())
data class RsvpRequest(val accepted:Boolean,val companions:List<String> = emptyList())
data class CheckRequest(val quantity:Int=1,val memberIndex:Int?=null)
data class QrCheckRequest(val qrPayload:String,val quantity:Int=1,val memberIndex:Int?=null)
data class SeatAssignmentRequest(val table:String?=null,val seat:String?=null,val sector:String?=null)
data class GuestEventResponse(val id:UUID,val name:String,val type:String,val description:String?,val startsAt:Instant,val endsAt:Instant?,val timezone:String,val location:String?,val status:EventStatus)
data class GuestModuleResponse(val code:String,val name:String,val category:String,val description:String,val order:Int,val featured:Boolean,val configuration:Map<String,Any?>)
data class GuestMapPointResponse(val id:UUID,val type:String,val title:String?,val payload:Map<String,Any?>)
data class GuestExperienceResponse(val invitation:InvitationResponse,val event:GuestEventResponse,val modules:List<GuestModuleResponse>,val agenda:List<AgendaItem>,val now:AgendaItem?,val next:AgendaItem?,val notifications:List<NotificationEntity>,val mapPoints:List<GuestMapPointResponse>)
data class GuestAssistanceRequest(val category:String,val details:String?=null,val location:String?=null)
data class LinkInvitationsRequest(val tokens:List<String> = emptyList())

@Service class InvitationService(private val repo:InvitationRepositoryPort,private val logs:GuestAccessLogRepositoryPort,private val events:EventRepositoryPort,private val eventModules:EventModuleRepositoryPort,private val catalog:ModuleCatalogRepositoryPort,private val agenda:AgendaItemRepositoryPort,private val notifications:NotificationRepositoryPort,private val records:ModuleRecordRepositoryPort,private val assistance:AssistanceRequestRepositoryPort,private val eventService:EventService,private val auth:AuthService,private val mapper:JsonCodec){
    private val random=SecureRandom()
    @Transactional fun create(userId:UUID,eventId:UUID,r:CreateInvitationRequest):InvitationResponse{eventService.owned(userId,eventId);eventService.requireModule(eventId,"INV");if(r.guestName.isBlank()||r.allowedCapacity<1)throw BadRequestException("Nombre y cupo válidos son obligatorios");checkSeat(eventId,null,r.table,r.seat,r.allowedCapacity);val raw=ByteArray(32).also(random::nextBytes).let{Base64.getUrlEncoder().withoutPadding().encodeToString(it)};val i=repo.save(Invitation(eventId=eventId,guestName=r.guestName.trim(),guestEmail=r.guestEmail?.lowercase(),tokenHash=auth.hash(raw),tokenExpiresAt=r.expiresAt?:Instant.now().plus(365,ChronoUnit.DAYS),allowedCapacity=r.allowedCapacity,tableLabel=r.table?.trim(),seatLabel=r.seat?.trim(),sectorLabel=r.sector?.trim()));return response(i,raw)}
    @Transactional(readOnly=true) fun list(userId:UUID,eventId:UUID):List<InvitationResponse>{eventService.owned(userId,eventId);return repo.findAllByEventId(eventId).map{response(it)}}
    @Transactional(readOnly=true) fun access(raw:String):InvitationResponse=response(valid(raw))
    @Transactional(readOnly=true) fun experience(raw:String):GuestExperienceResponse=experience(valid(raw))
    @Transactional(readOnly=true) fun mine(userId:UUID):List<GuestExperienceResponse> = repo.findAllByLinkedUserId(userId)
        .filter { it.revokedAt==null && it.tokenExpiresAt?.isAfter(Instant.now())!=false }
        .map(::experience)
    @Transactional(readOnly=true) fun linkedExperience(userId:UUID,id:UUID):GuestExperienceResponse=experience(linked(userId,id))
    private fun experience(invitation:Invitation):GuestExperienceResponse{
        val event=events.findById(invitation.eventId).orElseThrow{NotFoundException("Evento no encontrado")}
        if(event.status==EventStatus.DRAFT)throw ForbiddenException("El evento todavía no está publicado")
        val modules=eventModules.findAllByEventIdOrderByDisplayOrder(invitation.eventId).filter{it.enabled&&map(it.configuration)["guestVisible"]!=false}.mapNotNull{em->catalog.findById(em.moduleCode).orElse(null)?.takeIf{it.globallyEnabled}?.let{c->GuestModuleResponse(c.code,c.name,c.category,c.description,em.displayOrder,em.featured,map(em.configuration))}}
        val enabled=modules.map{it.code}.toSet();val agendaItems=if("CAL" in enabled)agenda.findAllByEventIdOrderByStartsAt(invitation.eventId).filter{it.status!="CANCELLED"}else emptyList();val current=Instant.now()
        val visibleNotifications=if("NOT" in enabled)notifications.findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(invitation.eventId).filter{n->
            (n.recipientUserId!=null&&n.recipientUserId==invitation.linkedUserId)||
                (n.recipientUserId==null&&when(n.audienceType){
                    "ALL"->true
                    "TABLE"->invitation.tableLabel!=null&&invitation.tableLabel==n.audienceValue
                    "SECTOR"->invitation.sectorLabel!=null&&invitation.sectorLabel==n.audienceValue
                    else->false
                })
        }else emptyList()
        val mapPoints=if("MAP" in enabled)listOf("ZONE","POINT").flatMap{type->records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(invitation.eventId,"MAP",type)}.filter{it.status=="ACTIVE"&&map(it.payload)["visible"]!=false}.map{GuestMapPointResponse(requireNotNull(it.id),it.recordType,it.title,map(it.payload))}else emptyList()
        return GuestExperienceResponse(response(invitation),GuestEventResponse(requireNotNull(event.id),event.name,event.type,event.description,event.startsAt,event.endsAt,event.timezone,event.location,event.status),modules,agendaItems,agendaItems.firstOrNull{!current.isBefore(it.startsAt)&&current.isBefore(it.endsAt)},agendaItems.firstOrNull{it.startsAt.isAfter(current)},visibleNotifications,mapPoints)
    }
    @Transactional fun rsvp(raw:String,r:RsvpRequest):InvitationResponse{val i=valid(raw);ensureRsvpOpen(i.eventId);if(r.companions.size+1>i.allowedCapacity)throw ConflictException("El cupo autorizado es ${i.allowedCapacity}");i.status=if(r.accepted)InvitationStatus.ACCEPTED else InvitationStatus.DECLINED;i.companions=mapper.write(r.companions);return response(i)}
    @Transactional fun linkedRsvp(userId:UUID,id:UUID,r:RsvpRequest):InvitationResponse{val i=linked(userId,id);ensureRsvpOpen(i.eventId);if(r.companions.size+1>i.allowedCapacity)throw ConflictException("El cupo autorizado es ${i.allowedCapacity}");i.status=if(r.accepted)InvitationStatus.ACCEPTED else InvitationStatus.DECLINED;i.companions=mapper.write(r.companions);return response(i)}
    @Transactional(readOnly=true) fun assistanceHistory(raw:String):List<AssistanceRequest>{val i=valid(raw);return assistance.findAllByInvitationIdOrderByCreatedAtDesc(requireNotNull(i.id))}
    @Transactional(readOnly=true) fun linkedAssistanceHistory(userId:UUID,id:UUID):List<AssistanceRequest>{val i=linked(userId,id);return assistance.findAllByInvitationIdOrderByCreatedAtDesc(requireNotNull(i.id))}
    @Transactional fun assistance(raw:String,r:GuestAssistanceRequest):AssistanceRequest{val i=valid(raw);if(r.category.isBlank())throw BadRequestException("Selecciona una categoría");eventService.requireAssistanceCategory(i.eventId,r.category);val category=r.category.trim().uppercase();val priority=if(category in setOf("FIRST_AID","EMERGENCY","PRIMEROS_AUXILIOS"))100 else 0;return assistance.save(AssistanceRequest(eventId=i.eventId,invitationId=requireNotNull(i.id),requesterUserId=i.linkedUserId,category=category,details=r.details?.trim(),location=r.location?.trim(),priority=priority))}
    @Transactional fun linkedAssistance(userId:UUID,id:UUID,r:GuestAssistanceRequest):AssistanceRequest{val i=linked(userId,id);if(r.category.isBlank())throw BadRequestException("Selecciona una categoría");eventService.requireAssistanceCategory(i.eventId,r.category);val category=r.category.trim().uppercase();val priority=if(category in setOf("FIRST_AID","EMERGENCY","PRIMEROS_AUXILIOS"))100 else 0;return assistance.save(AssistanceRequest(eventId=i.eventId,invitationId=requireNotNull(i.id),requesterUserId=userId,category=category,details=r.details?.trim(),location=r.location?.trim(),priority=priority))}
    @Transactional fun revoke(userId:UUID,eventId:UUID,id:UUID):InvitationResponse{eventService.owned(userId,eventId);val i=find(eventId,id);i.revokedAt=Instant.now();return response(i)}
    @Transactional fun assignSeat(userId:UUID,eventId:UUID,id:UUID,r:SeatAssignmentRequest):InvitationResponse{
        eventService.authorized(userId,eventId,"GUESTS")
        eventService.requireModule(eventId,"GST")
        val invitation=find(eventId,id)
        if(invitation.revokedAt!=null)throw ConflictException("La invitación está revocada")
        checkSeat(eventId,id,r.table,r.seat,invitation.allowedCapacity)
        invitation.tableLabel=r.table?.trim()?.ifBlank{null}
        invitation.seatLabel=r.seat?.trim()?.ifBlank{null}
        invitation.sectorLabel=r.sector?.trim()?.ifBlank{null}
        return response(invitation)
    }
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
    @Transactional fun check(userId:UUID,eventId:UUID,id:UUID,r:CheckRequest,incoming:Boolean):InvitationResponse{
        eventService.authorized(userId,eventId,"CHECK_IN")
        eventService.requireModule(eventId,"GST")
        val invitation=find(eventId,id)
        if(invitation.revokedAt!=null||invitation.tokenExpiresAt?.isBefore(Instant.now())==true)throw ConflictException("La invitación no está vigente")
        if(invitation.status!=InvitationStatus.ACCEPTED)throw ConflictException("La invitación no está confirmada")
        if(r.quantity<1)throw BadRequestException("Cantidad inválida")
        val history=logs.findAllByInvitationIdOrderByCreatedAt(id)
        val current=history.sumOf{if(it.action=="CHECK_IN")it.quantity else -it.quantity}.coerceAtLeast(0)
        val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")}
        if(event.status !in setOf(EventStatus.PUBLISHED,EventStatus.RUNNING))throw ConflictException("El evento no admite ingresos en su estado actual")
        if(r.memberIndex!=null){
            val names=listOf(invitation.guestName)+mapper.readStringList(invitation.companions)
            if(r.quantity!=1||r.memberIndex !in names.indices)throw BadRequestException("Integrante inválido")
            if(history.any{it.memberIndex==null})throw ConflictException("El grupo ya usa registro agregado")
            val memberHistory=history.filter{it.memberIndex==r.memberIndex}
            val inside=memberHistory.sumOf{if(it.action=="CHECK_IN")1 else -1}>0
            if(incoming){
                if(inside||current>=invitation.allowedCapacity)throw ConflictException("El integrante ya ingresó o el cupo se agotó")
                if(memberHistory.any{it.action=="CHECK_OUT"}&&!event.reentryAllowed)throw ConflictException("El reingreso no está permitido")
            }else if(!inside)throw ConflictException("El integrante no está dentro")
            logs.save(GuestAccessLog(invitationId=id,action=if(incoming)"CHECK_IN" else "CHECK_OUT",memberIndex=r.memberIndex,performedBy=userId))
        }else{
            if(history.any{it.memberIndex!=null})throw ConflictException("El grupo ya usa registro individual")
            if(incoming){
                if(current+r.quantity>invitation.allowedCapacity)throw ConflictException("El ingreso supera el cupo autorizado")
                if(current==0&&history.any{it.action=="CHECK_OUT"}&&!event.reentryAllowed)throw ConflictException("El reingreso no está permitido")
            }else if(r.quantity>current)throw ConflictException("No puede salir más personas de las ingresadas")
            logs.save(GuestAccessLog(invitationId=id,action=if(incoming)"CHECK_IN" else "CHECK_OUT",quantity=r.quantity,performedBy=userId))
        }
        return response(invitation)
    }
    @Transactional fun checkQr(userId:UUID,eventId:UUID,r:QrCheckRequest,incoming:Boolean):InvitationResponse {
        if(!r.qrPayload.startsWith("eventflow:invite:"))throw BadRequestException("QR inválido")
        val credential=r.qrPayload.removePrefix("eventflow:invite:")
        if(!credential.matches(Regex("[0-9a-f]{64}")))throw BadRequestException("QR inválido")
        val invitation=repo.findByTokenHash(credential)?:throw NotFoundException("Invitación no encontrada")
        if(invitation.eventId!=eventId)throw NotFoundException("Invitación no encontrada")
        return check(userId,eventId,requireNotNull(invitation.id),CheckRequest(r.quantity,r.memberIndex),incoming)
    }
    private fun valid(raw:String):Invitation{val i=repo.findByTokenHash(auth.hash(raw))?:throw UnauthorizedException("Invitación inválida");if(i.revokedAt!=null||i.tokenExpiresAt?.isBefore(Instant.now())==true)throw UnauthorizedException("Invitación expirada o revocada");return i}
    private fun linked(userId:UUID,id:UUID):Invitation{val i=repo.findById(id).orElseThrow{NotFoundException("Invitación no encontrada")};if(i.linkedUserId!=userId)throw ForbiddenException("La invitación no pertenece a tu cuenta");if(i.revokedAt!=null||i.tokenExpiresAt?.isBefore(Instant.now())==true)throw UnauthorizedException("Invitación expirada o revocada");return i}
    private fun find(eventId:UUID,id:UUID)=repo.findLocked(id)?.also{if(it.eventId!=eventId)throw NotFoundException("Invitación no encontrada")}?:throw NotFoundException("Invitación no encontrada")
    private fun count(i:Invitation)=logs.findAllByInvitationIdOrderByCreatedAt(requireNotNull(i.id)).sumOf{if(it.action=="CHECK_IN")it.quantity else -it.quantity}.coerceAtLeast(0)
    private fun checkSeat(eventId:UUID,invitationId:UUID?,tableRaw:String?,seatRaw:String?,capacity:Int){
        val table=tableRaw?.trim()?.ifBlank{null}
        val seat=seatRaw?.trim()?.ifBlank{null}
        if(seat!=null&&table==null)throw BadRequestException("El asiento requiere mesa")
        if(table==null)return
        val area=records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"GST","SEATING_AREA")
            .firstOrNull{it.status=="ACTIVE"&&it.title.equals(table,true)}
        val limit=area?.let { configured ->
            val locked=records.findLocked(requireNotNull(configured.id))?:throw NotFoundException("Mesa no encontrada")
            locked.capacity
        }
        val assignments=repo.findAllByEventId(eventId).filter{it.revokedAt==null&&it.id!=invitationId&&it.tableLabel.equals(table,true)}
        if(limit!=null&&assignments.sumOf{it.allowedCapacity}+capacity>limit)throw ConflictException("La mesa no tiene cupo disponible")
        if(seat!=null&&assignments.any{it.seatLabel.equals(seat,true)})
            throw ConflictException("El asiento ya está asignado")
    }
    private fun ensureRsvpOpen(eventId:UUID) {
        eventService.requireModule(eventId,"INV")
        val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")}
        if(event.status in setOf(EventStatus.FINISHED,EventStatus.CANCELLED))throw ConflictException("El RSVP está cerrado")
        val configuration=eventModules.findById(EventModuleId(eventId,"INV")).map{mapper.readMap(it.configuration)}.orElse(emptyMap())
        if(configuration["rsvpOpen"]==false)throw ConflictException("El RSVP está cerrado")
        val closesAt=configuration["rsvpClosesAt"]?.toString()?.let(Instant::parse)
        if(closesAt!=null&&!Instant.now().isBefore(closesAt))throw ConflictException("El RSVP está cerrado")
    }
    @Suppress("UNCHECKED_CAST") private fun map(json:String)=mapper.readMap(json)
    @Suppress("UNCHECKED_CAST") private fun response(i:Invitation,raw:String?=null):InvitationResponse{
        val companions=mapper.readStringList(i.companions)
        val history=logs.findAllByInvitationIdOrderByCreatedAt(requireNotNull(i.id))
        val individual=history.any{it.memberIndex!=null}
        val members=(listOf(i.guestName)+companions).mapIndexed{index,name->
            val inside=if(individual)history.filter{it.memberIndex==index}.sumOf{if(it.action=="CHECK_IN")1 else -1}>0 else null
            GroupMemberStatus(index,name,inside)
        }
        val current=history.sumOf{if(it.action=="CHECK_IN")it.quantity else -it.quantity}.coerceAtLeast(0)
        return InvitationResponse(requireNotNull(i.id),i.eventId,i.guestName,i.guestEmail,i.status,i.allowedCapacity,companions,
            i.tableLabel,i.seatLabel,i.sectorLabel,raw,current,"eventflow:invite:${i.tokenHash}",members)
    }
}
