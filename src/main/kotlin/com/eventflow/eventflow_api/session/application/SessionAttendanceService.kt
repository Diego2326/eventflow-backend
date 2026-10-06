package com.eventflow.eventflow_api.session.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.gamification.application.GamificationService
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.invitation.domain.InvitationStatus
import com.eventflow.eventflow_api.module.application.port.ModuleActionRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleAction
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class SessionAttendanceResponse(val sessionId:UUID,val invitationId:UUID,val attendeeUserId:UUID,val checkedInAt:Instant)

@Service class SessionAttendanceService(private val events:EventService,private val records:ModuleRecordRepositoryPort,
    private val actions:ModuleActionRepositoryPort,private val invitations:InvitationRepositoryPort,
    private val gamification:GamificationService){
    @Transactional fun checkIn(staffUserId:UUID,eventId:UUID,sessionId:UUID,invitationId:UUID):SessionAttendanceResponse{
        val event=events.authorized(staffUserId,eventId,"CHECK_IN")
        events.requireModule(eventId,"SES")
        if(event.status !in setOf(EventStatus.PUBLISHED,EventStatus.RUNNING))throw ConflictException("El evento no admite asistencia")
        val session=records.findLocked(sessionId)?:throw NotFoundException("Sesión no encontrada")
        if(session.eventId!=eventId||session.moduleCode!="SES"||session.recordType!="SESSION"||session.status!="ACTIVE")
            throw NotFoundException("Sesión no encontrada")
        val invitation=invitations.findById(invitationId).orElseThrow{NotFoundException("Invitación no encontrada")}
        if(invitation.eventId!=eventId||invitation.revokedAt!=null||invitation.status!=InvitationStatus.ACCEPTED||
            invitation.tokenExpiresAt?.isBefore(Instant.now())==true)throw ConflictException("La invitación no está vigente")
        val attendee=invitation.linkedUserId?:throw ConflictException("La invitación no está vinculada a una cuenta")
        if(actions.existsByModuleRecordIdAndActorUserIdAndActionType(sessionId,attendee,"CHECK_IN"))
            throw ConflictException("La asistencia ya fue registrada")
        if(session.capacity!=null&&session.currentCount>=session.capacity!!)throw ConflictException("La sesión no tiene cupo")
        session.currentCount++
        val saved=actions.save(ModuleAction(moduleRecordId=sessionId,actorUserId=attendee,invitationId=invitationId,
            actionType="CHECK_IN",uniqueAction=true))
        gamification.onEvidence(attendee,eventId)
        return SessionAttendanceResponse(sessionId,invitationId,attendee,saved.createdAt)
    }
}
