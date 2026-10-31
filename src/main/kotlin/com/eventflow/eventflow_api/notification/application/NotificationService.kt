package com.eventflow.eventflow_api.notification.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.notification.application.port.NotificationRepositoryPort
import com.eventflow.eventflow_api.notification.application.port.NotificationReadRepositoryPort
import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.notification.domain.NotificationRead
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleActionRepositoryPort
import com.eventflow.eventflow_api.ticketing.application.port.EventTicketPort
import com.eventflow.eventflow_api.ticketing.application.port.TicketOrderPort
import org.springframework.context.ApplicationEventPublisher
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class NotificationRequest(val title:String,val body:String,val audienceType:String="ALL",val audienceValue:String?=null,val recipientUserId:UUID?=null,val channel:String="IN_APP")
data class EventPushRequested(val eventId: UUID, val notificationId: UUID, val userIds: Set<UUID>, val title: String, val body: String)

@Service class NotificationService(private val eventService:EventService, private val notifications:NotificationRepositoryPort, private val invitations:InvitationRepositoryPort,
    private val records:ModuleRecordRepositoryPort,private val actions:ModuleActionRepositoryPort,
    private val ticketOrders:TicketOrderPort,private val tickets:EventTicketPort,private val publisher:ApplicationEventPublisher,private val reads:NotificationReadRepositoryPort){
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
        val notification=notifications.save(NotificationEntity(eventId=eventId,authorUserId=userId,recipientUserId=r.recipientUserId,title=r.title,body=r.body,audienceType=audience,audienceValue=r.audienceValue,channel="IN_APP"))
        val recipients=pushRecipients(eventId,audience,r).filter{it!=userId}.toSet()
        if(recipients.isNotEmpty())publisher.publishEvent(EventPushRequested(eventId,requireNotNull(notification.id),recipients,notification.title,notification.body))
        return notification
    }

    private fun pushRecipients(eventId:UUID,audience:String,r:NotificationRequest):Set<UUID>{
        val current=Instant.now()
        val activeInvitations=invitations.findAllByEventId(eventId).filter{it.revokedAt==null&&it.tokenExpiresAt?.isAfter(current)!=false}
        val linked=activeInvitations.mapNotNull{invitation->invitation.linkedUserId?.let{it to invitation}}
        val inviteRecipients=when(audience){
            "ALL"->linked.map{it.first}
            "TABLE"->linked.filter{it.second.tableLabel==r.audienceValue}.map{it.first}
            "SECTOR"->linked.filter{it.second.sectorLabel==r.audienceValue}.map{it.first}
            "USER"->listOfNotNull(r.recipientUserId)
            "SESSION","TRANSPORT"->{
                val recordId=UUID.fromString(requireNotNull(r.audienceValue))
                val acquire=if(audience=="SESSION")"CHECK_IN" else "RESERVE"
                val release=if(audience=="SESSION")"CHECK_OUT" else "CANCEL"
                val history=actions.findAllByModuleRecordIdInAndActionType(listOf(recordId),acquire)+actions.findAllByModuleRecordIdInAndActionType(listOf(recordId),release)
                history.groupBy{it.actorUserId}.mapNotNull{(actor,items)->actor?.takeIf{(items.filter{it.actionType==acquire}.sumOf{it.quantity}-items.filter{it.actionType==release}.sumOf{it.quantity})>0}}
            }
            else->emptyList()
        }.toMutableSet()
        if(audience=="ALL"){
            ticketOrders.findAllByEventIdOrderByCreatedAtDesc(eventId).filter{it.status=="CONFIRMED"}.forEach{order->
                if(tickets.findAllByOrderId(requireNotNull(order.id)).any{it.status=="ACTIVE"})inviteRecipients.add(order.buyerUserId)
            }
        }
        return inviteRecipients
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
        val visible=notifications.findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId).filter{n->manager||n.recipientUserId==userId||
            (n.recipientUserId==null&&when(n.audienceType){
                "ALL"->true;"TABLE"->inv.any{it.tableLabel==n.audienceValue};"SECTOR"->inv.any{it.sectorLabel==n.audienceValue}
                "SESSION"->holds(n.audienceValue,"CHECK_IN","CHECK_OUT")
                "TRANSPORT"->holds(n.audienceValue,"RESERVE","CANCEL")
                else->false
            })}
        attachReadState(userId,visible)
        return visible
    }
    @Transactional(readOnly=true) fun notification(userId:UUID,eventId:UUID,id:UUID):NotificationEntity {
        val visible=notifications(userId,eventId).firstOrNull{it.id==id}?:throw com.eventflow.eventflow_api.shared.application.error.NotFoundException("Notificación no encontrada")
        return visible
    }
    @Transactional(readOnly=true) fun personalNotifications(userId:UUID):List<NotificationEntity> =
        notifications.findAllByRecipientUserIdAndActiveTrueOrderByCreatedAtDesc(userId).also { attachReadState(userId,it) }
    @Transactional fun markPersonalRead(userId:UUID,id:UUID):NotificationEntity {
        val notification=notifications.findById(id).orElseThrow{com.eventflow.eventflow_api.shared.application.error.NotFoundException("Notificación no encontrada")}
        if(!notification.active || notification.recipientUserId!=userId)
            throw com.eventflow.eventflow_api.shared.application.error.NotFoundException("Notificación no encontrada")
        if(reads.findByUserIdAndNotificationId(userId,id)==null)reads.save(NotificationRead(userId=userId,notificationId=id))
        notification.readAt=reads.findByUserIdAndNotificationId(userId,id)?.readAt
        return notification
    }
    @Transactional fun markRead(userId:UUID,eventId:UUID,id:UUID):NotificationEntity {
        val notification=notification(userId,eventId,id)
        if(reads.findByUserIdAndNotificationId(userId,id)==null)reads.save(NotificationRead(userId=userId,notificationId=id))
        notification.readAt=reads.findByUserIdAndNotificationId(userId,id)?.readAt
        return notification
    }
    @Transactional fun markAllRead(userId:UUID,eventId:UUID):Int {
        val visible=notifications(userId,eventId);val ids=visible.mapNotNull{it.id}.toSet()
        val read=reads.findAllByUserIdAndNotificationIdIn(userId,ids).map{it.notificationId}.toSet()
        (ids-read).forEach{id->reads.save(NotificationRead(userId=userId,notificationId=id))}
        return ids.size-read.size
    }
    private fun attachReadState(userId:UUID,items:List<NotificationEntity>){
        val ids=items.mapNotNull{it.id};if(ids.isEmpty())return
        val times=reads.findAllByUserIdAndNotificationIdIn(userId,ids).associate{it.notificationId to it.readAt}
        items.forEach{it.readAt=it.id?.let(times::get)}
    }
}
