package com.eventflow.eventflow_api.transport.application

import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.ModuleRecordResponse
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class RouteDetails(val route:ModuleRecordResponse,val departures:List<ModuleRecordResponse>)

@Service class TransportRouteService(private val modules:ModuleDataService){
    @Transactional(readOnly=true) fun details(userId:UUID,eventId:UUID,routeId:UUID):RouteDetails{
        val route=modules.get(userId,eventId,routeId)
        if(route.moduleCode!="TRN"||route.recordType!="ROUTE"||route.status!="ACTIVE")throw NotFoundException("Ruta no encontrada")
        return RouteDetails(route,modules.list(userId,eventId,"TRN","DEPARTURE")
            .filter{it.parentRecordId==routeId&&it.status=="ACTIVE"})
    }
}
