package com.eventflow.eventflow_api.agenda.application

import com.eventflow.eventflow_api.agenda.application.port.AgendaFavoriteRepositoryPort
import com.eventflow.eventflow_api.agenda.application.port.AgendaItemRepositoryPort
import com.eventflow.eventflow_api.agenda.domain.AgendaFavorite
import com.eventflow.eventflow_api.agenda.domain.AgendaFavoriteId
import com.eventflow.eventflow_api.agenda.domain.AgendaItem
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AgendaRequest(val title:String,val description:String?=null,val startsAt:Instant,val endsAt:Instant,val zone:String?=null,val responsible:String?=null,val capacity:Int?=null)
data class NowNextResponse(val now:AgendaItem?,val next:AgendaItem?)

@Service class AgendaService(private val eventService:EventService, private val agenda:AgendaItemRepositoryPort, private val favorites:AgendaFavoriteRepositoryPort){
    @Transactional fun addAgenda(userId:UUID,eventId:UUID,r:AgendaRequest):AgendaItem{eventService.authorized(userId,eventId,"AGENDA");eventService.requireModule(eventId,"CAL");if(r.title.isBlank()||!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Actividad inválida");return agenda.save(AgendaItem(eventId=eventId,title=r.title,description=r.description,startsAt=r.startsAt,endsAt=r.endsAt,zone=r.zone,responsible=r.responsible,capacity=r.capacity))}
    @Transactional fun updateAgenda(userId:UUID,eventId:UUID,id:UUID,r:AgendaRequest):AgendaItem{eventService.authorized(userId,eventId,"AGENDA");val i=item(eventId,id);if(!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Horario inválido");i.title=r.title;i.description=r.description;i.startsAt=r.startsAt;i.endsAt=r.endsAt;i.zone=r.zone;i.responsible=r.responsible;i.capacity=r.capacity;return i}
    @Transactional fun deleteAgenda(userId:UUID,eventId:UUID,id:UUID){eventService.authorized(userId,eventId,"AGENDA");val i=item(eventId,id);i.status="CANCELLED"}
    @Transactional(readOnly=true) fun agenda(userId:UUID,eventId:UUID):List<AgendaItem>{eventService.requireVisibleModule(userId,eventId,"CAL");return agenda.findAllByEventIdOrderByStartsAt(eventId)}
    @Transactional(readOnly=true) fun nowNext(userId:UUID,eventId:UUID):NowNextResponse{val all=agenda(userId,eventId).filter{it.status!="CANCELLED"};val now=Instant.now();return NowNextResponse(all.firstOrNull{!now.isBefore(it.startsAt)&&now.isBefore(it.endsAt)},all.firstOrNull{it.startsAt.isAfter(now)})}
    @Transactional(readOnly=true) fun myAgenda(userId:UUID,eventId:UUID):List<AgendaItem>{
        eventService.requireVisibleModule(userId,eventId,"CAL")
        val favoriteIds=favorites.findAllByUserId(userId).map{it.agendaItemId}.toSet()
        return agenda.findAllByEventIdOrderByStartsAt(eventId).filter{it.id in favoriteIds&&it.status!="CANCELLED"}
    }
    @Transactional fun favorite(userId:UUID,eventId:UUID,id:UUID,on:Boolean){eventService.requireVisibleModule(userId,eventId,"CAL");val activity=item(eventId,id);if(on&&activity.status=="CANCELLED")throw ConflictException("La actividad está cancelada");val key=AgendaFavoriteId(id,userId);if(on&&!favorites.existsById(key))favorites.save(AgendaFavorite(id,userId))else if(!on)favorites.deleteById(key)}
    private fun item(eventId:UUID,id:UUID)=agenda.findById(id).orElseThrow{NotFoundException("Actividad no encontrada")}.also{if(it.eventId!=eventId)throw NotFoundException("Actividad no encontrada")}
}
