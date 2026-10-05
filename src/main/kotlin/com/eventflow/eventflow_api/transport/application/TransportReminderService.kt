package com.eventflow.eventflow_api.transport.application

import com.eventflow.eventflow_api.auth.application.port.NotificationPreferenceRepositoryPort
import com.eventflow.eventflow_api.auth.domain.NotificationPreferenceId
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.module.application.port.ModuleActionRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.notification.application.port.NotificationRepositoryPort
import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service class TransportReminderService(private val records:ModuleRecordRepositoryPort,
    private val actions:ModuleActionRepositoryPort,private val notifications:NotificationRepositoryPort,
    private val preferences:NotificationPreferenceRepositoryPort,private val events:EventService){

    @Transactional fun generate(now:Instant=Instant.now()):Int{
        val departures=records.findAllByModuleCodeAndRecordTypeAndStartsAtBetweenAndStatus("TRN","DEPARTURE",now,now.plus(Duration.ofMinutes(30)),"ACTIVE")
        if(departures.isEmpty())return 0
        val ids=departures.map{requireNotNull(it.id)}
        val reservations=(actions.findAllByModuleRecordIdInAndActionType(ids,"RESERVE")+
            actions.findAllByModuleRecordIdInAndActionType(ids,"CANCEL")).groupBy{it.moduleRecordId}
        var created=0
        departures.forEach { departure ->
            val active=mutableMapOf<java.util.UUID,Int>()
            reservations[departure.id].orEmpty().sortedBy{it.createdAt}.forEach{action->
                action.actorUserId?.let{actor->active[actor]=(active[actor]?:0)+(if(action.actionType=="RESERVE")action.quantity else -action.quantity)}
            }
            active.filterValues{it>0}.keys.forEach{recipient->
                val key="TRANSPORT:${departure.id}:$recipient"
                if(notifications.existsByDedupeKey(key))return@forEach
                if(preferences.findById(NotificationPreferenceId(recipient,"IN_APP")).map{!it.enabled}.orElse(false))return@forEach
                val event=try{
                    events.requireVisibleModule(recipient,departure.eventId,"TRN")
                    events.requireVisibleModule(recipient,departure.eventId,"NOT")
                    events.accessible(recipient,departure.eventId)
                }catch(_:ForbiddenException){return@forEach}catch(_:ConflictException){return@forEach}
                if(event.status !in setOf(EventStatus.PUBLISHED,EventStatus.RUNNING))return@forEach
                notifications.save(NotificationEntity(eventId=departure.eventId,recipientUserId=recipient,
                    title="Salida próxima",body="${departure.title?:"Tu transporte"} sale pronto",audienceType="USER",dedupeKey=key))
                created++
            }
        }
        return created
    }
}
