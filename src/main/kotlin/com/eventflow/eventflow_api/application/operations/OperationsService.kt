package com.eventflow.eventflow_api.application.operations

import com.eventflow.eventflow_api.application.port.*

import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.application.event.EventService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AgendaRequest(val title:String,val description:String?=null,val startsAt:Instant,val endsAt:Instant,val zone:String?=null,val responsible:String?=null,val capacity:Int?=null)
data class NowNextResponse(val now:AgendaItem?,val next:AgendaItem?)
data class AssistanceRequestDto(val category:String,val details:String?=null,val location:String?=null,val invitationId:UUID?=null)
data class AssistanceUpdate(val status:AssistanceStatus,val assignedUserId:UUID?=null)
data class NotificationRequest(val title:String,val body:String,val audienceType:String="ALL",val audienceValue:String?=null,val recipientUserId:UUID?=null,val channel:String="IN_APP")
data class MessageRequest(val channel:String,val body:String,val recipientUserId:UUID?=null,val reservationId:UUID?=null)

@Service class OperationsService(private val eventService:EventService,private val agenda:AgendaItemRepositoryPort,private val favorites:AgendaFavoriteRepositoryPort,private val requests:AssistanceRequestRepositoryPort,private val notifications:NotificationRepositoryPort,private val messages:ConversationMessageRepositoryPort,private val audits:AuditLogRepositoryPort,private val invitations:InvitationRepositoryPort,private val reservations:ReservationRepositoryPort,private val offerings:MarketplaceOfferingRepositoryPort){
    @Transactional fun addAgenda(userId:UUID,eventId:UUID,r:AgendaRequest):AgendaItem{eventService.authorized(userId,eventId,"AGENDA");eventService.requireModule(eventId,"CAL");if(r.title.isBlank()||!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Actividad inválida");return agenda.save(AgendaItem(eventId=eventId,title=r.title,description=r.description,startsAt=r.startsAt,endsAt=r.endsAt,zone=r.zone,responsible=r.responsible,capacity=r.capacity))}
    @Transactional fun updateAgenda(userId:UUID,eventId:UUID,id:UUID,r:AgendaRequest):AgendaItem{eventService.authorized(userId,eventId,"AGENDA");val i=item(eventId,id);if(!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Horario inválido");i.title=r.title;i.description=r.description;i.startsAt=r.startsAt;i.endsAt=r.endsAt;i.zone=r.zone;i.responsible=r.responsible;i.capacity=r.capacity;return i}
    @Transactional fun deleteAgenda(userId:UUID,eventId:UUID,id:UUID){eventService.authorized(userId,eventId,"AGENDA");val i=item(eventId,id);i.status="CANCELLED"}
    @Transactional(readOnly=true) fun agenda(userId:UUID,eventId:UUID):List<AgendaItem>{eventService.requireVisibleModule(userId,eventId,"CAL");return agenda.findAllByEventIdOrderByStartsAt(eventId)}
    @Transactional(readOnly=true) fun nowNext(userId:UUID,eventId:UUID):NowNextResponse{val all=agenda(userId,eventId).filter{it.status!="CANCELLED"};val now=Instant.now();return NowNextResponse(all.firstOrNull{!now.isBefore(it.startsAt)&&now.isBefore(it.endsAt)},all.firstOrNull{it.startsAt.isAfter(now)})}
    @Transactional(readOnly=true) fun myAgenda(userId:UUID,eventId:UUID):List<AgendaItem>{
        eventService.requireVisibleModule(userId,eventId,"CAL")
        val favoriteIds=favorites.findAllByUserId(userId).map{it.agendaItemId}.toSet()
        return agenda.findAllByEventIdOrderByStartsAt(eventId).filter{it.id in favoriteIds&&it.status!="CANCELLED"}
    }
    @Transactional fun favorite(userId:UUID,eventId:UUID,id:UUID,on:Boolean){eventService.requireVisibleModule(userId,eventId,"CAL");val activity=item(eventId,id);if(on&&activity.status=="CANCELLED")throw ConflictException("La actividad está cancelada");val key=AgendaFavoriteId(id,userId);if(on&&!favorites.existsById(key))favorites.save(AgendaFavorite(id,userId))else if(!on)favorites.deleteById(key)}
    @Transactional fun assistance(userId:UUID,eventId:UUID,r:AssistanceRequestDto):AssistanceRequest{
        eventService.requireVisibleModule(userId,eventId,"AST")
        if(r.category.isBlank())throw BadRequestException("Selecciona una categoría")
        eventService.requireAssistanceCategory(eventId,r.category)
        r.invitationId?.let { id ->
            val invitation=invitations.findById(id).orElseThrow{NotFoundException("Invitación no encontrada")}
            if(invitation.eventId!=eventId||invitation.linkedUserId!=userId||invitation.revokedAt!=null||invitation.tokenExpiresAt?.isBefore(Instant.now())==true)
                throw ForbiddenException("La invitación no pertenece a tu cuenta")
        }
        val priority=if(r.category.uppercase() in setOf("FIRST_AID","EMERGENCY","PRIMEROS_AUXILIOS"))100 else 0
        return requests.save(AssistanceRequest(eventId=eventId,invitationId=r.invitationId,requesterUserId=userId,category=r.category.uppercase(),details=r.details,location=r.location,priority=priority))
    }
    @Transactional(readOnly=true) fun assistanceList(userId:UUID,eventId:UUID):List<AssistanceRequest>{
        val categories=eventService.assistanceCategories(userId,eventId)
        return requests.findAllByEventIdOrderByPriorityDescCreatedAtAsc(eventId).filter{categories==null||it.category in categories}
    }
    @Transactional fun assistanceUpdate(userId:UUID,eventId:UUID,id:UUID,r:AssistanceUpdate):AssistanceRequest{
        val categories=eventService.assistanceCategories(userId,eventId)
        val q=requests.findById(id).orElseThrow{NotFoundException("Solicitud no encontrada")}
        if(q.eventId!=eventId)throw NotFoundException("Solicitud no encontrada")
        if(categories!=null&&q.category !in categories)throw ForbiddenException("No tienes permiso para esta categoría")
        val allowed=mapOf(
            AssistanceStatus.RECEIVED to setOf(AssistanceStatus.ACCEPTED,AssistanceStatus.CANCELLED),
            AssistanceStatus.ACCEPTED to setOf(AssistanceStatus.ON_THE_WAY,AssistanceStatus.ATTENDED,AssistanceStatus.CANCELLED),
            AssistanceStatus.ON_THE_WAY to setOf(AssistanceStatus.ATTENDED,AssistanceStatus.CANCELLED),
            AssistanceStatus.ATTENDED to emptySet(), AssistanceStatus.CANCELLED to emptySet()
        )
        if(r.status !in allowed.getValue(q.status))throw ConflictException("Transición de solicitud no permitida")
        q.status=r.status;q.assignedUserId=r.assignedUserId?:q.assignedUserId;q.updatedAt=Instant.now()
        audits.save(AuditLog(actorUserId=userId,eventId=eventId,action="ASSISTANCE_${r.status}",targetType="ASSISTANCE",targetId=id))
        return q
    }
    @Transactional fun notify(userId:UUID,eventId:UUID,r:NotificationRequest):NotificationEntity{
        eventService.authorized(userId,eventId,"NOTIFICATIONS");eventService.requireModule(eventId,"NOT")
        if(r.title.isBlank()||r.body.isBlank())throw BadRequestException("Título y mensaje son obligatorios")
        val audience=r.audienceType.uppercase()
        if(audience !in setOf("ALL","TABLE","SECTOR","USER"))throw BadRequestException("Audiencia inválida")
        if(audience in setOf("TABLE","SECTOR")&&r.audienceValue.isNullOrBlank())throw BadRequestException("La audiencia requiere un valor")
        if(audience=="USER"&&r.recipientUserId==null)throw BadRequestException("Selecciona un destinatario")
        if(r.recipientUserId!=null&&invitations.findAllByLinkedUserId(r.recipientUserId).none{it.eventId==eventId&&it.revokedAt==null})throw BadRequestException("El destinatario no pertenece al evento")
        if(r.channel.uppercase()!="IN_APP")throw BadRequestException("El canal no está disponible")
        return notifications.save(NotificationEntity(eventId=eventId,authorUserId=userId,recipientUserId=r.recipientUserId,title=r.title,body=r.body,audienceType=audience,audienceValue=r.audienceValue,channel="IN_APP"))
    }
    @Transactional(readOnly=true) fun notifications(userId:UUID,eventId:UUID):List<NotificationEntity>{eventService.requireVisibleModule(userId,eventId,"NOT");val inv=invitations.findAllByLinkedUserId(userId).filter{it.eventId==eventId&&it.revokedAt==null&&it.tokenExpiresAt?.isBefore(Instant.now())!=true};val manager=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false};return notifications.findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId).filter{n->manager||n.recipientUserId==userId||(n.recipientUserId==null&&when(n.audienceType){"ALL"->true;"TABLE"->inv.any{it.tableLabel==n.audienceValue};"SECTOR"->inv.any{it.sectorLabel==n.audienceValue};else->false})}}
    @Transactional fun message(userId:UUID,eventId:UUID,r:MessageRequest):ConversationMessage{
        eventService.requireModule(eventId,"MSG")
        if(r.body.isBlank())throw BadRequestException("El mensaje está vacío")
        if(r.reservationId!=null) {
            val reservation=reservations.findById(r.reservationId).orElseThrow{NotFoundException("Reservación no encontrada")}
            if(reservation.eventId!=eventId)throw NotFoundException("Reservación no encontrada")
            val provider=offerings.findById(reservation.offeringId).orElseThrow{NotFoundException("Publicación no encontrada")}.ownerUserId
            if(userId !in setOf(reservation.requesterUserId,provider))throw ForbiddenException("No perteneces a esta conversación")
            val other=if(userId==provider)reservation.requesterUserId else provider
            if(r.recipientUserId!=null&&r.recipientUserId!=other)throw ForbiddenException("Destinatario inválido")
            return messages.save(ConversationMessage(eventId=eventId,reservationId=r.reservationId,senderUserId=userId,recipientUserId=other,channel=r.channel.uppercase(),body=r.body))
        }
        eventService.requireVisibleModule(userId,eventId,"MSG")
        val manager=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false}
        if(!manager&&(r.channel.uppercase()!="STAFF"||r.recipientUserId!=null))throw ForbiddenException("Solo puedes contactar al personal")
        if(manager&&r.recipientUserId!=null&&invitations.findAllByLinkedUserId(r.recipientUserId).none{it.eventId==eventId&&it.revokedAt==null})throw BadRequestException("El destinatario no pertenece al evento")
        return messages.save(ConversationMessage(eventId=eventId,senderUserId=userId,recipientUserId=r.recipientUserId,channel=r.channel.uppercase(),body=r.body))
    }
    @Transactional(readOnly=true) fun messages(userId:UUID,eventId:UUID,channel:String):List<ConversationMessage>{
        eventService.requireModule(eventId,"MSG")
        val manager=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false}
        if(manager)return messages.findAllByEventIdAndChannelOrderByCreatedAt(eventId,channel.uppercase())
        return messages.findVisible(eventId,channel.uppercase(),userId)
    }
    private fun item(eventId:UUID,id:UUID)=agenda.findById(id).orElseThrow{NotFoundException("Actividad no encontrada")}.also{if(it.eventId!=eventId)throw NotFoundException("Actividad no encontrada")}
}
