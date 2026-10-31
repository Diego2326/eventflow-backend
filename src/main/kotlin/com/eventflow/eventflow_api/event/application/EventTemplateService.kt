package com.eventflow.eventflow_api.event.application

import com.eventflow.eventflow_api.event.application.port.EventTemplateRepositoryPort
import com.eventflow.eventflow_api.event.domain.EventTemplates
import com.eventflow.eventflow_api.event.domain.StoredEventTemplate
import com.eventflow.eventflow_api.module.application.port.ModuleCatalogRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleDependencyRepositoryPort
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.port.JsonCodec
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class EventTemplateRequest(val name:String,val eventType:String,val moduleCodes:Set<String>,val description:String?=null)
data class EventTemplateResponse(val id:String,val name:String,val eventType:String,val moduleCodes:List<String>,val description:String?,val builtIn:Boolean)
data class ApplyEventTemplateRequest(val name:String,val startsAt:Instant,val endsAt:Instant?=null,val timezone:String="America/Guatemala",val location:String?=null,val estimatedCapacity:Int?=null,val budget:BigDecimal?=null,val description:String?=null,val reentryAllowed:Boolean=false)
data class AppliedEventTemplateResponse(val templateId:String,val event:EventResponse,val enabledModules:List<String>)

@Service class EventTemplateService(private val templates:EventTemplateRepositoryPort,private val catalog:ModuleCatalogRepositoryPort,
    private val dependencies:ModuleDependencyRepositoryPort,private val events:EventService,private val json:JsonCodec){

    @Transactional(readOnly=true) fun list(userId:UUID):List<EventTemplateResponse> =
        EventTemplates.types().mapNotNull{builtIn(it)}+templates.findAllByOwnerUserIdOrderByUpdatedAtDesc(userId).map(::response)

    @Transactional(readOnly=true) fun get(userId:UUID,id:String)=resolve(userId,id)

    @Transactional fun create(userId:UUID,r:EventTemplateRequest):EventTemplateResponse{
        val validated=validate(r)
        return response(templates.save(StoredEventTemplate(ownerUserId=userId,name=validated.first,eventType=validated.second,
            description=validated.third,moduleCodes=json.write(validated.fourth))))
    }

    @Transactional fun update(userId:UUID,id:String,r:EventTemplateRequest):EventTemplateResponse{
        val template=customOwned(userId,id);val v=validate(r)
        template.name=v.first;template.eventType=v.second;template.description=v.third;template.moduleCodes=json.write(v.fourth);template.updatedAt=Instant.now()
        return response(templates.save(template))
    }

    @Transactional fun delete(userId:UUID,id:String){templates.deleteById(requireNotNull(customOwned(userId,id).id))}

    @Transactional fun apply(userId:UUID,id:String,r:ApplyEventTemplateRequest):AppliedEventTemplateResponse{
        val template=resolve(userId,id)
        val modules=expand(template.moduleCodes.toSet())
        if(r.name.isBlank()||r.name.length>200||r.estimatedCapacity!=null&&r.estimatedCapacity<1||r.budget!=null&&r.budget<BigDecimal.ZERO)
            throw BadRequestException("Datos del evento inválidos")
        if(runCatching{ZoneId.of(r.timezone)}.isFailure)throw BadRequestException("Zona horaria inválida")
        if(r.endsAt!=null&&!r.endsAt.isAfter(r.startsAt))throw BadRequestException("El fin debe ser posterior al inicio")
        val event=events.create(userId,CreateEventRequest(r.name,template.eventType,r.startsAt,r.endsAt,r.timezone,r.location,r.estimatedCapacity,r.budget,r.description,r.reentryAllowed))
        modules.forEachIndexed{index,code->events.configureModule(userId,event.id,code,ConfigureModuleRequest(enabled=true,order=index))}
        return AppliedEventTemplateResponse(template.id,event,modules)
    }

    private fun resolve(userId:UUID,id:String):EventTemplateResponse{
        if(id.startsWith("builtin-"))return builtIn(id.removePrefix("builtin-"))?:throw NotFoundException("Plantilla no encontrada")
        val uuid=runCatching{UUID.fromString(id)}.getOrNull()?:throw NotFoundException("Plantilla no encontrada")
        return templates.findByOwnerUserIdAndId(userId,uuid)?.let(::response)?:throw NotFoundException("Plantilla no encontrada")
    }

    private fun builtIn(type:String):EventTemplateResponse?{
        if(type !in EventTemplates.types())return null
        val modules=EventTemplates.modulesFor(type)
        return EventTemplateResponse("builtin-$type",type.replace('_',' ').lowercase().replaceFirstChar{it.uppercase()},type,modules,"Plantilla integrada de evento $type",true)
    }

    private fun validate(r:EventTemplateRequest):Validated{
        val name=r.name.trim();val type=r.eventType.trim().uppercase()
        if(name.isEmpty()||name.length>120||!type.matches(Regex("[A-Z][A-Z0-9_]{1,39}"))||(r.description?.length?:0)>1000)
            throw BadRequestException("Datos de plantilla inválidos")
        val raw=r.moduleCodes.map{it.trim().uppercase()}.toSet()
        if(raw.size!=r.moduleCodes.size||raw.any{!it.matches(Regex("[A-Z0-9_]{2,12}"))})throw BadRequestException("Códigos de módulo inválidos")
        return Validated(name,type,r.description?.trim()?.ifEmpty{null},expand(raw))
    }

    private fun expand(requested:Set<String>):List<String>{
        val expanded=linkedSetOf<String>()
        fun visit(code:String,stack:Set<String>){
            if(code in stack)throw ConflictException("La dependencia de módulos contiene un ciclo")
            val module=catalog.findById(code).orElse(null)
            if(module==null||!module.globallyEnabled)throw ConflictException("El módulo $code no está disponible")
            if(code in expanded)return
            dependencies.findAllByModuleCode(code).sortedBy{it.requiredModuleCode}.forEach{visit(it.requiredModuleCode,stack+code)}
            expanded.add(code)
        }
        requested.sorted().forEach{visit(it,emptySet())}
        return expanded.toList()
    }

    private fun customOwned(userId:UUID,id:String):StoredEventTemplate{
        val uuid=runCatching{UUID.fromString(id)}.getOrNull()?:throw NotFoundException("Plantilla no encontrada")
        return templates.findByOwnerUserIdAndId(userId,uuid)?:throw NotFoundException("Plantilla no encontrada")
    }
    private fun response(t:StoredEventTemplate)=EventTemplateResponse(requireNotNull(t.id).toString(),t.name,t.eventType,json.readStringList(t.moduleCodes),t.description,false)
    private data class Validated(val first:String,val second:String,val third:String?,val fourth:List<String>)
}
