package com.eventflow.eventflow_api.capacity.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.shared.application.port.JsonCodec
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class ZoneCapacityResponse(val id:UUID,val zoneId:UUID?,val name:String?,val capacity:Int,val occupied:Int,val available:Int,val state:String)
data class ServiceStateResponse(val id:UUID,val name:String?,val state:String)

@Service class CapacityService(private val eventService:EventService,private val records:ModuleRecordRepositoryPort,
    private val json:JsonCodec){
    @Transactional(readOnly=true) fun zones(userId:UUID,eventId:UUID):List<ZoneCapacityResponse>{
        eventService.requireVisibleModule(userId,eventId,"AFO")
        return records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"AFO","ZONE_CAPACITY")
            .filter{it.status=="ACTIVE"}.map{record->
                val capacity=requireNotNull(record.capacity)
                val occupied=json.readMap(record.payload)["occupied"].toString().toInt()
                val available=(capacity-occupied).coerceAtLeast(0)
                val state=when {available==0->"FULL";occupied.toLong()*10>=capacity.toLong()*8->"NEAR_LIMIT";else->"AVAILABLE"}
                val zoneId=runCatching{UUID.fromString(json.readMap(record.payload)["zoneId"]?.toString())}.getOrNull()
                ZoneCapacityResponse(requireNotNull(record.id),zoneId,record.title,capacity,occupied,available,state)
            }
    }
    @Transactional(readOnly=true) fun services(userId:UUID,eventId:UUID):List<ServiceStateResponse>{
        eventService.requireVisibleModule(userId,eventId,"AFO")
        return records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"AFO","SERVICE_STATUS")
            .filter{it.status=="ACTIVE"}.map{ServiceStateResponse(requireNotNull(it.id),it.title,json.readMap(it.payload)["state"].toString())}
    }
}
