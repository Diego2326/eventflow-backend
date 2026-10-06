package com.eventflow.eventflow_api.sport.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.application.port.EventRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleRecord
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.port.JsonCodec
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class MatchResultRequest(val scoreA:Int,val scoreB:Int)
data class MatchResultResponse(val matchId:UUID,val scoreA:Int,val scoreB:Int,val winnerId:UUID,val nextMatchId:UUID?)

@Service class MatchResultService(private val eventService:EventService,private val events:EventRepositoryPort,
    private val records:ModuleRecordRepositoryPort,private val json:JsonCodec){
    @Transactional fun report(staffUserId:UUID,eventId:UUID,matchId:UUID,r:MatchResultRequest):MatchResultResponse{
        eventService.authorized(staffUserId,eventId,"SPORTS")
        eventService.requireModule(eventId,"SPT")
        if(r.scoreA<0||r.scoreB<0||r.scoreA==r.scoreB)throw BadRequestException("El resultado requiere un ganador")
        events.findLocked(eventId)?:throw NotFoundException("Evento no encontrado")
        val match=records.findLocked(matchId)?:throw NotFoundException("Partido no encontrado")
        if(match.eventId!=eventId||match.moduleCode!="SPT"||match.recordType!="MATCH")throw NotFoundException("Partido no encontrado")
        if(match.status!="ACTIVE")throw ConflictException("El partido ya fue cerrado")
        val data=json.readMap(match.payload)
        val first=uuid(data["participantAId"])
        val second=uuid(data["participantBId"])
        if(first==null||second==null)throw ConflictException("El partido aún no tiene ambos participantes")
        val winner=if(r.scoreA>r.scoreB)first else second
        val nextId=uuid(data["nextMatchId"])
        if(nextId!=null){
            if(nextId==matchId)throw BadRequestException("Un partido no puede avanzar a sí mismo")
            val next=records.findLocked(nextId)?:throw NotFoundException("Siguiente partido no encontrado")
            if(next.eventId!=eventId||next.moduleCode!="SPT"||next.recordType!="MATCH"||next.status!="ACTIVE")
                throw BadRequestException("Siguiente partido inválido")
            val slot=data["nextSlot"]?.toString()?.uppercase()
            if(slot !in setOf("A","B"))throw BadRequestException("Selecciona el lado del siguiente partido")
            val field=if(slot=="A")"participantAId" else "participantBId"
            val nextData=json.readMap(next.payload).toMutableMap()
            if(nextData["category"]!=data["category"])throw BadRequestException("El siguiente partido debe pertenecer a la misma categoría")
            val round=data["round"]?.toString()?.toIntOrNull()?:1
            val nextRound=nextData["round"]?.toString()?.toIntOrNull()?:1
            if(nextRound<=round)throw BadRequestException("El siguiente partido debe ser de una ronda posterior")
            if(uuid(nextData[field])!=null)throw ConflictException("El lado del siguiente partido ya está ocupado")
            if(uuid(nextData[if(slot=="A")"participantBId" else "participantAId"])==winner)
                throw ConflictException("El participante ya está en el siguiente partido")
            nextData[field]=winner.toString()
            next.payload=json.write(nextData)
            next.updatedAt=Instant.now()
        }
        match.payload=json.write(data+mapOf("scoreA" to r.scoreA,"scoreB" to r.scoreB,"winnerId" to winner.toString()))
        match.status="COMPLETED"
        match.updatedAt=Instant.now()
        return MatchResultResponse(matchId,r.scoreA,r.scoreB,winner,nextId)
    }
    private fun uuid(value:Any?):UUID?=runCatching{UUID.fromString(value?.toString())}.getOrNull()
}
