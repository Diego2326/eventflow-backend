package com.eventflow.eventflow_api.messaging.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.marketplace.application.port.MarketplaceOfferingRepositoryPort
import com.eventflow.eventflow_api.marketplace.application.port.ReservationRepositoryPort
import com.eventflow.eventflow_api.messaging.application.port.ConversationMessageRepositoryPort
import com.eventflow.eventflow_api.messaging.domain.ConversationMessage
import com.eventflow.eventflow_api.messaging.domain.MessageReadReceipt
import com.eventflow.eventflow_api.messaging.application.port.MessageReadReceiptPort
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class MessageRequest(val channel:String,val body:String,val recipientUserId:UUID?=null,val reservationId:UUID?=null)
data class ConversationSummary(val conversationId:String,val eventId:UUID,val channel:String,val lastMessage:ConversationMessage)
data class MessageReadState(val messageId:UUID,val senderUserId:UUID,val recipientUserId:UUID?,val readBy:List<MessageReadReceipt>)
data class MarkMessagesReadResponse(val conversationId:String,val markedCount:Int)

@Service class MessagingService(private val eventService:EventService, private val messages:ConversationMessageRepositoryPort, private val invitations:InvitationRepositoryPort, private val reservations:ReservationRepositoryPort, private val offerings:MarketplaceOfferingRepositoryPort,private val receipts:MessageReadReceiptPort){
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
        if(!eventService.guestMessageChannelOpen(eventId,r.channel))throw ConflictException("El canal de atención está cerrado")
        val manager=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false}
        if(!manager&&r.recipientUserId!=null)throw ForbiddenException("Solo puedes contactar al personal")
        if(manager&&r.recipientUserId!=null&&invitations.findAllByLinkedUserId(r.recipientUserId).none{it.eventId==eventId&&it.revokedAt==null})throw BadRequestException("El destinatario no pertenece al evento")
        return messages.save(ConversationMessage(eventId=eventId,senderUserId=userId,recipientUserId=r.recipientUserId,channel=r.channel.uppercase(),body=r.body))
    }
    @Transactional(readOnly=true) fun messages(userId:UUID,eventId:UUID,channel:String):List<ConversationMessage>{
        eventService.requireModule(eventId,"MSG")
        val manager=try{eventService.owned(userId,eventId);true}catch(_:ForbiddenException){false}
        if(manager)return messages.findAllByEventIdAndChannelOrderByCreatedAt(eventId,channel.uppercase()).filter{message->
            val reservationId=message.reservationId
            if(reservationId==null)message.recipientUserId==null||message.senderUserId==userId||message.recipientUserId==userId
            else reservationParticipant(userId,eventId,reservationId)
        }
        return messages.findVisible(eventId,channel.uppercase(),userId)
    }
    @Transactional(readOnly=true) fun conversations(userId:UUID,eventId:UUID,channel:String):List<ConversationSummary> =
        messages(userId,eventId,channel).groupBy(::conversationId).mapNotNull{(id,items)->items.maxByOrNull{it.createdAt}?.let{ConversationSummary(id,eventId,channel.uppercase(),it)}}.sortedByDescending{it.lastMessage.createdAt}

    @Transactional(readOnly=true) fun conversation(userId:UUID,eventId:UUID,channel:String,id:String):List<ConversationMessage> {
        if(id.length>100||id.isBlank())throw BadRequestException("Conversación inválida")
        return messages(userId,eventId,channel).filter{conversationId(it)==id}
            .ifEmpty{throw com.eventflow.eventflow_api.shared.application.error.NotFoundException("Conversación no encontrada")}
    }

    @Transactional fun markConversationRead(userId:UUID,eventId:UUID,channel:String,id:String):MarkMessagesReadResponse{
        val thread=conversation(userId,eventId,channel,id)
        val unread=thread.filter{it.senderUserId!=userId&&receipts.findByMessageIdAndUserId(requireNotNull(it.id),userId)==null}
        unread.forEach{receipts.save(MessageReadReceipt(messageId=requireNotNull(it.id),userId=userId))}
        return MarkMessagesReadResponse(id,unread.size)
    }

    @Transactional(readOnly=true) fun readState(userId:UUID,eventId:UUID,channel:String,id:String):List<MessageReadState>{
        val thread=conversation(userId,eventId,channel,id);val ids=thread.mapNotNull{it.id}
        val byMessage=if(ids.isEmpty())emptyMap()else receipts.findAllByMessageIdIn(ids).groupBy{it.messageId}
        return thread.map{MessageReadState(requireNotNull(it.id),it.senderUserId,it.recipientUserId,byMessage[it.id].orEmpty().sortedBy{receipt->receipt.readAt})}
    }

    private fun conversationId(message:ConversationMessage):String {
        message.reservationId?.let{return "reservation-$it"}
        val recipient=message.recipientUserId
        if(recipient!=null){val ids=listOf(message.senderUserId,recipient).sortedBy(UUID::toString);return "pair-${ids[0]}-${ids[1]}"}
        return "member-${message.senderUserId}"
    }

    private fun reservationParticipant(userId:UUID,eventId:UUID,reservationId:UUID):Boolean{
        val reservation=reservations.findById(reservationId).orElse(null)?:return false
        if(reservation.eventId!=eventId)return false
        val provider=offerings.findById(reservation.offeringId).orElse(null)?.ownerUserId?:return false
        return userId==reservation.requesterUserId||userId==provider
    }

    @Transactional(readOnly=true)
    fun authorizeSocketAccess(userId:UUID,eventId:UUID){
        eventService.requireModule(eventId,"MSG")
        try { eventService.accessible(userId,eventId); return } catch(_:ForbiddenException) { }
        val reservationParticipant=reservations.findAllByEventId(eventId).any{reservation->
            reservation.requesterUserId==userId || offerings.findById(reservation.offeringId).orElse(null)?.ownerUserId==userId
        }
        if(!reservationParticipant)throw ForbiddenException("No tienes acceso a conversaciones de este evento")
    }
}
