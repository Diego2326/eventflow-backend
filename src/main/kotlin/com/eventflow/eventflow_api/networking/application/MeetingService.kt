package com.eventflow.eventflow_api.networking.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.application.port.EventRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleRecord
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.port.JsonCodec
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class MeetingRequest(val participantUserId:UUID,val startsAt:Instant,val endsAt:Instant,val location:String?=null)
data class MeetingResponse(val id:UUID,val requesterUserId:UUID,val participantUserId:UUID,val startsAt:Instant,val endsAt:Instant,val location:String?,val status:String)

@Service class MeetingService(private val eventService:EventService,private val events:EventRepositoryPort,
    private val records:ModuleRecordRepositoryPort,private val json:JsonCodec){

    @Transactional fun request(userId:UUID,eventId:UUID,r:MeetingRequest):MeetingResponse{
        eventService.requireVisibleModule(userId,eventId,"NET")
        if(r.participantUserId==userId)throw BadRequestException("Selecciona otro participante")
        if(!r.endsAt.isAfter(r.startsAt)||!r.startsAt.isAfter(Instant.now())||r.endsAt.isAfter(r.startsAt.plusSeconds(7200)))throw BadRequestException("Horario de reunión inválido")
        if(r.location!=null&&r.location.length>120)throw BadRequestException("Ubicación demasiado larga")
        val profiles=records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"NET","PROFILE")
        if(listOf(userId,r.participantUserId).any{id->profiles.none{it.ownerUserId==id&&it.status=="ACTIVE"&&json.readMap(it.payload)["visible"]==true}})
            throw ConflictException("Ambos participantes deben tener un perfil visible")
        val saved=records.save(ModuleRecord(eventId=eventId,moduleCode="NET",recordType="MEETING",ownerUserId=userId,
            status="PENDING",payload=json.write(mapOf("participantUserId" to r.participantUserId.toString(),"location" to r.location)),
            startsAt=r.startsAt,endsAt=r.endsAt))
        return response(saved)
    }

    @Transactional fun decide(userId:UUID,eventId:UUID,id:UUID,accept:Boolean):MeetingResponse{
        eventService.requireVisibleModule(userId,eventId,"NET")
        events.findLocked(eventId)?:throw NotFoundException("Evento no encontrado")
        val meeting=records.findLocked(id)?:throw NotFoundException("Reunión no encontrada")
        if(meeting.eventId!=eventId||meeting.moduleCode!="NET"||meeting.recordType!="MEETING")throw NotFoundException("Reunión no encontrada")
        val participant=participant(meeting)
        if(userId!=participant)throw ForbiddenException("Solo el invitado puede responder")
        if(meeting.status!="PENDING")throw ConflictException("La reunión ya fue respondida")
        if(accept){
            val start=requireNotNull(meeting.startsAt);val end=requireNotNull(meeting.endsAt)
            if(!start.isAfter(Instant.now()))throw ConflictException("El horario ya pasó")
            val people=setOf(requireNotNull(meeting.ownerUserId),participant)
            val conflict=records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"NET","MEETING")
                .any{it.id!=id&&it.status=="ACCEPTED"&&it.startsAt!!<end&&it.endsAt!!>start&&
                    (it.ownerUserId in people||participant(it) in people)}
            if(conflict)throw ConflictException("El horario coincide con otra reunión")
        }
        meeting.status=if(accept)"ACCEPTED" else "DECLINED"
        meeting.updatedAt=Instant.now()
        return response(meeting)
    }

    @Transactional fun cancel(userId:UUID,eventId:UUID,id:UUID):MeetingResponse{
        eventService.requireVisibleModule(userId,eventId,"NET")
        val meeting=records.findLocked(id)?:throw NotFoundException("Reunión no encontrada")
        if(meeting.eventId!=eventId||meeting.moduleCode!="NET"||meeting.recordType!="MEETING")throw NotFoundException("Reunión no encontrada")
        if(userId!=meeting.ownerUserId&&userId!=participant(meeting))throw ForbiddenException("No participas en esta reunión")
        if(meeting.status !in setOf("PENDING","ACCEPTED"))throw ConflictException("La reunión ya está cerrada")
        meeting.status="CANCELLED";meeting.updatedAt=Instant.now()
        return response(meeting)
    }

    @Transactional(readOnly=true) fun mine(userId:UUID,eventId:UUID):List<MeetingResponse>{
        eventService.requireVisibleModule(userId,eventId,"NET")
        return records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"NET","MEETING")
            .filter{it.ownerUserId==userId||participant(it)==userId}.map(::response)
    }

    private fun participant(record:ModuleRecord)=runCatching{UUID.fromString(json.readMap(record.payload)["participantUserId"].toString())}
        .getOrElse{throw ConflictException("Participante inválido")}
    private fun response(record:ModuleRecord)=MeetingResponse(requireNotNull(record.id),requireNotNull(record.ownerUserId),participant(record),
        requireNotNull(record.startsAt),requireNotNull(record.endsAt),json.readMap(record.payload)["location"]?.toString(),record.status)
}
