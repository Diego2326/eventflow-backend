package com.eventflow.eventflow_api.notification.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.notification.application.port.NotificationRepositoryPort
import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class NotificationRequest(val title:String,val body:String,val audienceType:String="ALL",val audienceValue:String?=null,val recipientUserId:UUID?=null,val channel:String="IN_APP")

@Service class NotificationService(private val eventService:EventService, private val notifications:NotificationRepositoryPort, private val invitations:InvitationRepositoryPort){
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
}
