package com.eventflow.eventflow_api.exhibition.application

import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.ModuleRecordResponse
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class ExhibitorDetails(val exhibitor:ModuleRecordResponse,val stands:List<ModuleRecordResponse>,val resources:List<ModuleRecordResponse>)

@Service class ExhibitionService(private val modules:ModuleDataService){
    @Transactional(readOnly=true) fun details(userId:UUID,eventId:UUID,exhibitorId:UUID):ExhibitorDetails{
        val exhibitor=modules.get(userId,eventId,exhibitorId)
        if(exhibitor.moduleCode!="EXH"||exhibitor.recordType!="EXHIBITOR"||exhibitor.status!="ACTIVE")throw NotFoundException("Expositor no encontrado")
        val stands=modules.list(userId,eventId,"EXH","STAND").filter{it.parentRecordId==exhibitorId&&it.status=="ACTIVE"}
        val sources=(stands.map{it.id}+exhibitorId).map(UUID::toString).toSet()
        val resources=try{modules.list(userId,eventId,"RSC","RESOURCE")}catch(_:ConflictException){emptyList()}
            .filter{it.status=="ACTIVE"&&it.payload["sourceRecordId"]?.toString() in sources}
        return ExhibitorDetails(exhibitor,stands,resources)
    }
}
