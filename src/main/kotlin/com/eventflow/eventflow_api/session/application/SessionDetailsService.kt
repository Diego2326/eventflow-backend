package com.eventflow.eventflow_api.session.application

import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.ModuleRecordResponse
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class SessionDetails(val session:ModuleRecordResponse,val speaker:ModuleRecordResponse,val resources:List<ModuleRecordResponse>)

@Service class SessionDetailsService(private val modules:ModuleDataService){
    @Transactional(readOnly=true) fun details(userId:UUID,eventId:UUID,sessionId:UUID):SessionDetails{
        val session=modules.get(userId,eventId,sessionId)
        if(session.moduleCode!="SES"||session.recordType!="SESSION"||session.status!="ACTIVE")throw NotFoundException("Sesión no encontrada")
        val speaker=modules.get(userId,eventId,session.parentRecordId?:throw NotFoundException("Ponente no encontrado"))
        if(speaker.moduleCode!="SES"||speaker.recordType!="SPEAKER"||speaker.status!="ACTIVE")throw NotFoundException("Ponente no encontrado")
        val resources=try{modules.list(userId,eventId,"RSC","RESOURCE")}catch(_:ConflictException){emptyList()}
            .filter{it.status=="ACTIVE"&&it.payload["sourceRecordId"]?.toString()==sessionId.toString()}
        return SessionDetails(session,speaker,resources)
    }
}
