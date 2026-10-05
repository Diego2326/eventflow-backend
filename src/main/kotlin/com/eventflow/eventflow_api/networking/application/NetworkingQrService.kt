package com.eventflow.eventflow_api.networking.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.port.JsonCodec
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class NetworkingQrResponse(val profileId:UUID,val value:String)
data class SharedProfileResponse(val profileId:UUID,val displayName:String?,val bio:String?,val organization:String?,
    val interests:List<String>,val contact:String?)

@Service class NetworkingQrService(private val events:EventService,private val records:ModuleRecordRepositoryPort,
    private val json:JsonCodec){
    @Transactional(readOnly=true) fun qr(userId:UUID,eventId:UUID):NetworkingQrResponse{
        events.requireVisibleModule(userId,eventId,"NET")
        val profile=records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"NET","PROFILE")
            .firstOrNull{it.ownerUserId==userId&&it.status=="ACTIVE"&&json.readMap(it.payload)["visible"]==true}
            ?:throw ConflictException("Activa tu perfil de networking")
        val id=requireNotNull(profile.id)
        return NetworkingQrResponse(id,"/api/events/$eventId/networking/profiles/$id")
    }
    @Transactional(readOnly=true) fun scan(userId:UUID,eventId:UUID,profileId:UUID):SharedProfileResponse{
        events.requireVisibleModule(userId,eventId,"NET")
        val profile=records.findById(profileId).orElseThrow{NotFoundException("Perfil no encontrado")}
        if(profile.eventId!=eventId||profile.moduleCode!="NET"||profile.recordType!="PROFILE"||profile.status!="ACTIVE")
            throw NotFoundException("Perfil no encontrado")
        val data=json.readMap(profile.payload)
        if(data["consent"]!=true||data["visible"]!=true)throw NotFoundException("Perfil no encontrado")
        return SharedProfileResponse(profileId,data["displayName"]?.toString(),data["bio"]?.toString(),
            data["organization"]?.toString(),(data["interests"] as? List<*>)?.map{it.toString()}?:emptyList(),
            data["contact"]?.toString())
    }
}
