package com.eventflow.eventflow_api.resource.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.module.application.ModuleActionRequest
import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class ResourceQrResponse(val resourceId:UUID,val value:String)

@Service class ResourceSharingService(private val events:EventService,private val records:ModuleRecordRepositoryPort,
    private val modules:ModuleDataService){
    @Transactional(readOnly=true) fun qr(userId:UUID,eventId:UUID,id:UUID):ResourceQrResponse{
        events.requireVisibleModule(userId,eventId,"RSC")
        val resource=records.findById(id).orElseThrow{NotFoundException("Recurso no encontrado")}
        if(resource.eventId!=eventId||resource.moduleCode!="RSC"||resource.recordType!="RESOURCE"||resource.status!="ACTIVE")
            throw NotFoundException("Recurso no encontrado")
        return ResourceQrResponse(id,"/api/events/$eventId/resources/$id")
    }
    @Transactional(readOnly=true) fun get(userId:UUID,eventId:UUID,id:UUID)=modules.get(userId,eventId,id).also{
        if(it.moduleCode!="RSC"||it.recordType!="RESOURCE")throw NotFoundException("Recurso no encontrado")
    }
    @Transactional fun save(userId:UUID,eventId:UUID,id:UUID)=modules.action(userId,eventId,id,ModuleActionRequest("SAVE"))
}
