package com.eventflow.eventflow_api.notification.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.notification.application.port.NotificationRepositoryPort
import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleActionRepositoryPort
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class NotificationRequest(val title:String,val body:String,val audienceType:String="ALL",val audienceValue:String?=null,val recipientUserId:UUID?=null,val channel:String="IN_APP")

@Service class NotificationService(private val eventService:EventService, private val notifications:NotificationRepositoryPort, private val invitations:InvitationRepositoryPort,
    private val records:ModuleRecordRepositoryPort,private val actions:ModuleActionRepositoryPort){
    @Transactional fun notify(userId:UUID,eventId:UUID,r:NotificationRequest):NotificationEntity{
        eventService.authorized(userId,eventId,"NOTIFICATIONS");eventService.requireModule(eventId,"NOT")
        if(r.title.isBlank()||r.body.isBlank())throw BadRequestException("Título y mensaje son obligatorios")
        val audience=r.audienceType.uppercase()
        if(audience !in setOf("ALL","TABLE","SECTOR","USER","SESSION","TRANSPORT"))throw BadRequestException("Audiencia inválida")
        if(audience in setOf("TABLE","SECTOR","SESSION","TRANSPORT")&&r.audienceValue.isNullOrBlank())throw BadRequestException("La audiencia requiere un valor")
        if(audience in setOf("SESSION","TRANSPORT")){
            val id=runCatching{UUID.fromString(r.audienceValue)}.getOrNull()?:throw BadRequestException("Registro de audiencia inválido")
            val record=records.findById(id).orElseThrow{BadRequestException("Registro de audiencia inválido")}
            val expected=if(audience=="SESSION")"SES" to "SESSION" else "TRN" to "DEPARTURE"
            if(record.eventId!=eventId||(record.moduleCode to record.recordType)!=expected)throw BadRequestException("Registro de audiencia inválido")
        }
        if(audience=="USER"&&r.recipientUserId==null)throw BadRequestException("Selecciona un destinatario")
        if(r.recipientUserId!=null&&invitations.findAllByLinkedUserId(r.recipientUserId).none{it.eventId==eventId&&it.revokedAt==null})throw BadRequestException("El destinatario no pertenece al evento")
        if(r.channel.uppercase()!="IN_APP")throw BadRequestException("El canal no está disponible")
        return notifications.save(NotificationEntity(eventId=eventId,authorUserId=userId,recipientUserId=r.recipientUserId,title=r.title,body=r.body,audienceType=audience,audienceValue=r.audienceValue,channel="IN_APP"))
    }
    @Transactional(readOnly=true) fun notifications(userId:UUID,eventId:UUID):List<NotificationEntity>{
        eventService.requireVisibleModule(userId,eventId,"NOT")
        val inv=invitations.findAllByLinkedUserId(userId).filter{it.eventId==eventId&&it.revokedAt==null&&it.tokenExpiresAt?.isBefore(Instant.now())!=true}
        val manager=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false}
        val scoped=actions.findAllByActorUserIdAndActionTypeInOrderByCreatedAt(userId,listOf("CHECK_IN","CHECK_OUT","RESERVE","CANCEL"))
            .groupBy{it.moduleRecordId}.mapValues{(_,history)->history.groupBy{it.actionType}}
        fun holds(value:String?,acquire:String,release:String):Boolean{
            val id=runCatching{UUID.fromString(value)}.getOrNull()?:return false
            val history=scoped[id]?:return false
            return (history[acquire]?.sumOf{it.quantity}?:0)>(history[release]?.sumOf{it.quantity}?:0)
        }
        return notifications.findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId).filter{n->manager||n.recipientUserId==userId||
            (n.recipientUserId==null&&when(n.audienceType){
                "ALL"->true;"TABLE"->inv.any{it.tableLabel==n.audienceValue};"SECTOR"->inv.any{it.sectorLabel==n.audienceValue}
                "SESSION"->holds(n.audienceValue,"CHECK_IN","CHECK_OUT")
                "TRANSPORT"->holds(n.audienceValue,"RESERVE","CANCEL")
                else->false
            })}
    }
}
