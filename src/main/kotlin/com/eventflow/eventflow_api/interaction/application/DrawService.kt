package com.eventflow.eventflow_api.interaction.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.application.port.EventRepositoryPort
import com.eventflow.eventflow_api.invitation.application.port.GuestAccessLogRepositoryPort
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleActionRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleAction
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.UUID

data class DrawWinnerResponse(val drawId:UUID,val invitationId:UUID,val userId:UUID?,val guestName:String)

@Service class DrawService(
    private val eventService:EventService,
    private val events:EventRepositoryPort,
    private val records:ModuleRecordRepositoryPort,
    private val actions:ModuleActionRepositoryPort,
    private val invitations:InvitationRepositoryPort,
    private val accessLogs:GuestAccessLogRepositoryPort
){
    private val random=SecureRandom()

    @Transactional fun run(userId:UUID,eventId:UUID,drawId:UUID):DrawWinnerResponse{
        eventService.owned(userId,eventId)
        eventService.requireModule(eventId,"INT")
        events.findLocked(eventId)?:throw NotFoundException("Evento no encontrado")
        val draw=records.findLocked(drawId)?:throw NotFoundException("Sorteo no encontrado")
        if(draw.eventId!=eventId||draw.moduleCode!="INT"||draw.recordType!="DRAW")throw NotFoundException("Sorteo no encontrado")
        if(draw.status!="ACTIVE")throw ConflictException("El sorteo está cerrado")
        if(actions.findAllByModuleRecordIdOrderByCreatedAt(drawId).any{it.actionType=="WIN"})throw ConflictException("El sorteo ya fue ejecutado")
        val priorWinners=records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"INT","DRAW")
            .map{requireNotNull(it.id)}.let{if(it.isEmpty())emptyList() else actions.findAllByModuleRecordIdInAndActionType(it,"WIN")}
            .mapNotNull{it.invitationId}.toSet()
        val invited=invitations.findAllByEventId(eventId)
        val attendance=accessLogs.findAllByInvitationIdIn(invited.map{requireNotNull(it.id)})
            .groupBy{it.invitationId}.mapValues{(_,logs)->logs.sumOf{if(it.action=="CHECK_IN")it.quantity else -it.quantity}}
        val candidates=invited.filter{inv->
            inv.revokedAt==null&&inv.id !in priorWinners&&(attendance[inv.id]?:0)>0
        }
        if(candidates.isEmpty())throw ConflictException("No hay invitados elegibles")
        val winner=candidates[random.nextInt(candidates.size)]
        actions.save(ModuleAction(moduleRecordId=drawId,actorUserId=winner.linkedUserId,invitationId=winner.id,
            actionType="WIN",uniqueAction=false))
        draw.status="CLOSED"
        return DrawWinnerResponse(drawId,requireNotNull(winner.id),winner.linkedUserId,winner.guestName)
    }
}
