package com.eventflow.eventflow_api.gamification.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.invitation.domain.InvitationStatus
import com.eventflow.eventflow_api.module.application.port.ModuleActionRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleAction
import com.eventflow.eventflow_api.module.domain.ModuleRecord
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.port.JsonCodec
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class MissionProgressResponse(val missionId:UUID,val title:String?,val completed:Int,val required:Int,val finished:Boolean)
data class GamificationResponse(val missions:List<MissionProgressResponse>,val badgeIds:List<UUID>)

@Service class GamificationService(private val events:EventService,private val records:ModuleRecordRepositoryPort,
    private val actions:ModuleActionRepositoryPort,private val invitations:InvitationRepositoryPort,private val json:JsonCodec){
    private val progressActions=listOf("CHECK_IN","VOTE","ANSWER","SAVE","RESERVE","JOIN")

    @Transactional fun completeMilestone(staffUserId:UUID,eventId:UUID,milestoneId:UUID,invitationId:UUID){
        val event=events.authorized(staffUserId,eventId,"CHECK_IN")
        events.requireModule(eventId,"GAM")
        if(event.status !in setOf(EventStatus.PUBLISHED,EventStatus.RUNNING))throw ConflictException("El evento no admite hitos")
        val milestone=records.findLocked(milestoneId)?:throw NotFoundException("Hito no encontrado")
        if(milestone.eventId!=eventId||milestone.moduleCode!="GAM"||milestone.recordType!="MILESTONE"||milestone.status!="ACTIVE")
            throw NotFoundException("Hito no encontrado")
        val invitation=invitations.findById(invitationId).orElseThrow{NotFoundException("Invitación no encontrada")}
        if(invitation.eventId!=eventId||invitation.revokedAt!=null||invitation.status!=InvitationStatus.ACCEPTED||
            invitation.tokenExpiresAt?.isBefore(Instant.now())==true)throw ConflictException("La invitación no está vigente")
        val attendee=invitation.linkedUserId?:throw ConflictException("Vincula la invitación a una cuenta")
        if(actions.existsByModuleRecordIdAndActorUserIdAndActionType(milestoneId,attendee,"CHECK_IN"))
            throw ConflictException("El hito ya está completo")
        actions.save(ModuleAction(moduleRecordId=milestoneId,actorUserId=attendee,invitationId=invitationId,actionType="CHECK_IN"))
    }

    @Transactional(readOnly=true) fun progress(userId:UUID,eventId:UUID):GamificationResponse{
        events.requireVisibleModule(userId,eventId,"GAM")
        val missions=records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"GAM","MISSION")
            .filter{it.status=="ACTIVE"}
        val completedActions=actions.findAllByActorUserIdAndActionTypeInOrderByCreatedAt(userId,progressActions)
            .map{it.moduleRecordId to it.actionType}.toSet()
        val progress=missions.map{mission->
            val requirements=requirements(mission)
            val count=requirements.count{it in completedActions}
            MissionProgressResponse(requireNotNull(mission.id),mission.title,count,requirements.size,count==requirements.size)
        }
        val awarded=actions.findAllByActorUserIdAndActionTypeInOrderByCreatedAt(userId,listOf("AWARD"))
            .map{it.moduleRecordId}.toSet()
        val badgeIds=records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"GAM","BADGE")
            .filter{it.status=="ACTIVE"&&it.id in awarded}
            .map{requireNotNull(it.id)}
        return GamificationResponse(progress,badgeIds)
    }

    @Transactional fun syncBadges(userId:UUID,eventId:UUID):GamificationResponse{
        val progress=progress(userId,eventId)
        val finished=progress.missions.filter{it.finished}.map{it.missionId.toString()}.toSet()
        records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"GAM","BADGE")
            .filter{it.status=="ACTIVE"&&json.readMap(it.payload)["missionId"]?.toString() in finished}
            .forEach{badge->
                val id=requireNotNull(badge.id)
                if(!actions.existsByModuleRecordIdAndActorUserIdAndActionType(id,userId,"AWARD"))
                    actions.save(ModuleAction(moduleRecordId=id,actorUserId=userId,actionType="AWARD"))
            }
        return progress(userId,eventId)
    }

    private fun requirements(mission:ModuleRecord):List<Pair<UUID,String>> = (json.readMap(mission.payload)["requirements"] as List<*>).map{
        val raw=it as Map<*,*>
        UUID.fromString(raw["recordId"].toString()) to raw["action"].toString()
    }
}
