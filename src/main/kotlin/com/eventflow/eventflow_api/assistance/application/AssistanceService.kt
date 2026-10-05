package com.eventflow.eventflow_api.assistance.application

import com.eventflow.eventflow_api.admin.application.port.AuditLogRepositoryPort
import com.eventflow.eventflow_api.admin.domain.AuditLog
import com.eventflow.eventflow_api.assistance.application.port.AssistanceRequestRepositoryPort
import com.eventflow.eventflow_api.assistance.domain.AssistanceRequest
import com.eventflow.eventflow_api.assistance.domain.AssistanceStatus
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AssistanceRequestDto(val category:String,val details:String?=null,val location:String?=null,val invitationId:UUID?=null)
data class AssistanceUpdate(val status:AssistanceStatus,val assignedUserId:UUID?=null)

@Service class AssistanceService(private val eventService:EventService, private val requests:AssistanceRequestRepositoryPort, private val audits:AuditLogRepositoryPort, private val invitations:InvitationRepositoryPort){
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
}
