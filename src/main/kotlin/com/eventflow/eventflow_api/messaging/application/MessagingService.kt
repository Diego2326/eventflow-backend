package com.eventflow.eventflow_api.messaging.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.marketplace.application.port.MarketplaceOfferingRepositoryPort
import com.eventflow.eventflow_api.marketplace.application.port.ReservationRepositoryPort
import com.eventflow.eventflow_api.messaging.application.port.ConversationMessageRepositoryPort
import com.eventflow.eventflow_api.messaging.domain.ConversationMessage
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class MessageRequest(val channel:String,val body:String,val recipientUserId:UUID?=null,val reservationId:UUID?=null)

@Service class MessagingService(private val eventService:EventService, private val messages:ConversationMessageRepositoryPort, private val invitations:InvitationRepositoryPort, private val reservations:ReservationRepositoryPort, private val offerings:MarketplaceOfferingRepositoryPort){
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
}
