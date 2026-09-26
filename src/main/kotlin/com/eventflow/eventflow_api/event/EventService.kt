package com.eventflow.eventflow_api.event

import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateEventRequest(val name: String, val type: String, val startsAt: Instant, val endsAt: Instant? = null,
    val timezone: String = "America/Guatemala", val location: String? = null, val estimatedCapacity: Int? = null,
    val budget: BigDecimal? = null, val description: String? = null, val reentryAllowed: Boolean = false)
data class UpdateEventRequest(val name: String? = null, val startsAt: Instant? = null, val endsAt: Instant? = null,
    val timezone: String? = null, val location: String? = null, val estimatedCapacity: Int? = null,
    val budget: BigDecimal? = null, val description: String? = null, val reentryAllowed: Boolean? = null)
data class EventResponse(val id: UUID, val name: String, val type: String, val startsAt: Instant, val endsAt: Instant?,
    val timezone: String, val location: String?, val estimatedCapacity: Int?, val budget: BigDecimal?, val description: String?, val status: EventStatus, val reentryAllowed: Boolean)
data class ConfigureModuleRequest(val enabled: Boolean = true, val order: Int = 0, val featured: Boolean = false, val configuration: Map<String, Any?> = emptyMap())
data class ModuleResponse(val code: String, val name: String, val category: String, val description: String, val enabled: Boolean = true,
    val order: Int = 0, val featured: Boolean = false, val configuration: Map<String, Any?> = emptyMap())
data class DashboardResponse(val event: EventResponse, val activeModules: Int, val invitations: Int, val acceptedGuests: Int,
    val checkedIn: Int, val reservations: Int, val pendingAssistance: Int)
data class CollaboratorRequest(val userId:UUID,val permissions:Set<String>)

@Service
class EventService(
    private val events: EventRepository, private val collaborators: EventCollaboratorRepository,
    private val catalog: ModuleCatalogRepository, private val dependencies: ModuleDependencyRepository,
    private val eventModules: EventModuleRepository, private val invitations: InvitationRepository,
    private val accessLogs: GuestAccessLogRepository, private val reservations: ReservationRepository,
    private val assistance: AssistanceRequestRepository, private val mapper: ObjectMapper
) {
    private val templates = mapOf(
        "WEDDING" to listOf("INV","GST","CAL","MAP","ORD","AST","GAL","INT","TRN","NOT"),
        "BIRTHDAY" to listOf("INV","GST","CAL","MAP","GAL","INT","GAM","ORD","NOT"),
        "GRADUATION" to listOf("INV","GST","CAL","MAP","GAL","RSC","NOT"),
        "CONFERENCE" to listOf("INV","GST","CAL","MAP","SES","INT","NET","RSC","QUE","NOT"),
        "CORPORATE" to listOf("INV","GST","CAL","MAP","NET","INT","RSC","NOT"),
        "EXPO" to listOf("INV","GST","MAP","EXH","GAM","QUE","NET","RSC","NOT"),
        "JOB_FAIR" to listOf("INV","GST","MAP","EXH","NET","QUE","RSC","NOT"),
        "FESTIVAL" to listOf("INV","GST","CAL","MAP","QUE","AFO","TRN","LNF","NOT"),
        "TOURNAMENT" to listOf("INV","GST","CAL","MAP","SPT","INT","AFO","NOT"),
        "HACKATHON" to listOf("INV","GST","CAL","MAP","NET","INT","RSC","BKG","NOT"),
        "WORKSHOP" to listOf("INV","GST","CAL","SES","INT","RSC","NOT"),
        "CAMP" to listOf("INV","GST","CAL","MAP","TRN","AST","AFO","NOT"),
        "TRIP" to listOf("INV","GST","CAL","MAP","TRN","NOT"),
        "GALA" to listOf("INV","GST","CAL","MAP","ORD","INT","GAL","NOT"),
        "CUSTOM" to emptyList()
    )

    @Transactional
    fun create(userId: UUID, request: CreateEventRequest): EventResponse {
        validateDates(request.startsAt, request.endsAt)
        if (request.name.isBlank()) throw BadRequestException("El nombre es obligatorio")
        val type = request.type.trim().uppercase()
        val event = events.save(EventEntity(ownerUserId=userId, name=request.name.trim(), type=type,
            description=request.description, startsAt=request.startsAt, endsAt=request.endsAt, timezone=request.timezone,
            location=request.location, estimatedCapacity=request.estimatedCapacity, budget=request.budget, reentryAllowed=request.reentryAllowed))
        templates.getOrDefault(type, emptyList()).forEachIndexed { index, code ->
            if (catalog.findById(code).map { it.globallyEnabled }.orElse(false)) eventModules.save(EventModule(requireNotNull(event.id), code, displayOrder=index))
        }
        return event.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID): List<EventResponse> {
        val direct = events.findAccessible(userId)
        val guestEventIds = invitations.findAllByLinkedUserId(userId).map { it.eventId }.toSet()
        val guests = if (guestEventIds.isEmpty()) emptyList() else events.findAllById(guestEventIds)
        return (direct + guests).distinctBy { it.id }.sortedBy { it.startsAt }.map { it.toResponse() }
    }
    @Transactional(readOnly = true) fun get(userId: UUID, eventId: UUID): EventResponse = accessible(userId,eventId).toResponse()
    @Transactional fun update(userId: UUID, eventId: UUID, r: UpdateEventRequest): EventResponse {
        val e=owned(userId,eventId); val start=r.startsAt?:e.startsAt; val end=r.endsAt?:e.endsAt; validateDates(start,end)
        r.name?.let { if(it.isBlank()) throw BadRequestException("El nombre es obligatorio") else e.name=it.trim() }
        e.startsAt=start; e.endsAt=end; r.timezone?.let{e.timezone=it}; r.location?.let{e.location=it}; r.estimatedCapacity?.let{e.estimatedCapacity=it}
        r.budget?.let{e.budget=it}; r.description?.let{e.description=it}; r.reentryAllowed?.let{e.reentryAllowed=it}; e.updatedAt=Instant.now(); return e.toResponse()
    }
    @Transactional fun transition(userId: UUID,eventId: UUID,status: EventStatus): EventResponse {
        val e=owned(userId,eventId)
        val allowed=mapOf(EventStatus.DRAFT to setOf(EventStatus.PUBLISHED,EventStatus.CANCELLED),EventStatus.PUBLISHED to setOf(EventStatus.RUNNING,EventStatus.CANCELLED),EventStatus.RUNNING to setOf(EventStatus.FINISHED,EventStatus.CANCELLED),EventStatus.FINISHED to emptySet(),EventStatus.CANCELLED to emptySet())
        if(status !in allowed.getValue(e.status)) throw ConflictException("Transición ${e.status} -> $status no permitida")
        if(status==EventStatus.PUBLISHED && eventModules.findAllByEventIdOrderByDisplayOrder(eventId).none{it.enabled}) throw ConflictException("Debes habilitar al menos un módulo")
        e.status=status; e.updatedAt=Instant.now(); return e.toResponse()
    }

    @Transactional(readOnly = true) fun catalog()=catalog.findAll().filter{it.globallyEnabled}.map{ModuleResponse(it.code,it.name,it.category,it.description)}
    @Transactional(readOnly = true) fun modules(userId: UUID,eventId: UUID,navigationOnly:Boolean=false): List<ModuleResponse> {
        accessible(userId,eventId)
        return eventModules.findAllByEventIdOrderByDisplayOrder(eventId).filter{it.enabled}.mapNotNull { em -> catalog.findById(em.moduleCode).orElse(null)?.takeIf{it.globallyEnabled}?.let { c ->
            ModuleResponse(c.code,c.name,c.category,c.description,true,em.displayOrder,em.featured,readMap(em.configuration)) } }
    }
    @Transactional fun configureModule(userId: UUID,eventId: UUID,codeRaw:String,r:ConfigureModuleRequest): ModuleResponse {
        owned(userId,eventId); val code=codeRaw.uppercase(); val c=catalog.findById(code).orElseThrow{NotFoundException("Módulo no encontrado")}
        if(!c.globallyEnabled) throw ConflictException("El módulo está deshabilitado globalmente")
        if(r.featured && !r.enabled) throw BadRequestException("Un módulo destacado debe estar habilitado")
        if(r.enabled) dependencies.findAllByModuleCode(code).forEach { dep ->
            val active=eventModules.findById(EventModuleId(eventId,dep.requiredModuleCode)).map{it.enabled}.orElse(false)
            if(!active) throw ConflictException("$code requiere el módulo ${dep.requiredModuleCode}")
        }
        if(!r.enabled) {
            val dependents=dependencies.findAll().filter{it.requiredModuleCode==code}.map{it.moduleCode}
            if(eventModules.findAllByEventIdOrderByDisplayOrder(eventId).any{it.enabled && it.moduleCode in dependents}) throw ConflictException("Otros módulos activos dependen de $code")
        }
        val em=eventModules.findById(EventModuleId(eventId,code)).orElse(EventModule(eventId,code))
        em.enabled=r.enabled; em.displayOrder=r.order; em.featured=r.featured; em.configuration=mapper.writeValueAsString(r.configuration); em.updatedAt=Instant.now(); eventModules.save(em)
        return ModuleResponse(c.code,c.name,c.category,c.description,em.enabled,em.displayOrder,em.featured,r.configuration)
    }
    @Transactional(readOnly = true) fun dashboard(userId:UUID,eventId:UUID):DashboardResponse {
        val e=owned(userId,eventId); val inv=invitations.findAllByEventId(eventId); val checked=inv.sumOf{i->accessLogs.findAllByInvitationIdOrderByCreatedAt(requireNotNull(i.id)).sumOf{if(it.action=="CHECK_IN")it.quantity else -it.quantity}.coerceAtLeast(0)}
        return DashboardResponse(e.toResponse(),eventModules.findAllByEventIdOrderByDisplayOrder(eventId).count{it.enabled},inv.size,inv.count{it.status==InvitationStatus.ACCEPTED},checked,reservations.findAllByEventId(eventId).size,assistance.findAllByEventIdOrderByPriorityDescCreatedAtAsc(eventId).count{it.status !in setOf(AssistanceStatus.ATTENDED,AssistanceStatus.CANCELLED)})
    }
    @Transactional fun addCollaborator(userId:UUID,eventId:UUID,r:CollaboratorRequest):EventCollaborator{ownerOnly(userId,eventId);if(r.userId==userId)throw BadRequestException("El propietario ya administra el evento");return collaborators.save(EventCollaborator(eventId,r.userId,r.permissions.map{it.uppercase()}.joinToString(",")))}
    @Transactional(readOnly=true) fun listCollaborators(userId:UUID,eventId:UUID):List<EventCollaborator>{ownerOnly(userId,eventId);return collaborators.findAllByEventId(eventId)}
    @Transactional fun removeCollaborator(userId:UUID,eventId:UUID,collaboratorId:UUID){ownerOnly(userId,eventId);collaborators.deleteById(EventCollaboratorId(eventId,collaboratorId))}
    fun ownerOnly(userId:UUID,eventId:UUID):EventEntity {val e=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(e.ownerUserId!=userId)throw ForbiddenException("Solo el propietario puede realizar esta operación");return e}
    fun owned(userId:UUID,eventId:UUID):EventEntity=authorized(userId,eventId,"ALL")
    fun authorized(userId:UUID,eventId:UUID,permission:String):EventEntity{val e=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(e.ownerUserId==userId)return e;val c=collaborators.findByEventIdAndUserId(eventId,userId)?:throw ForbiddenException("No tienes acceso a este evento");val p=c.permissions.split(',').map{it.trim().uppercase()};if("ALL" !in p&&permission.uppercase() !in p)throw ForbiddenException("No tienes el permiso $permission");return e}
    fun accessible(userId:UUID,eventId:UUID):EventEntity { val e=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")}; if(e.ownerUserId!=userId && !collaborators.existsByEventIdAndUserId(eventId,userId) && invitations.findAllByLinkedUserId(userId).none{it.eventId==eventId}) throw ForbiddenException("No tienes acceso a este evento"); return e }
    fun requireModule(eventId: UUID, code: String) { if(!eventModules.findById(EventModuleId(eventId,code)).map{it.enabled}.orElse(false)) throw ConflictException("El módulo $code no está habilitado") }
    private fun validateDates(start:Instant,end:Instant?){if(end!=null&&!end.isAfter(start))throw BadRequestException("La fecha de fin debe ser posterior al inicio")}
    @Suppress("UNCHECKED_CAST") private fun readMap(json:String):Map<String,Any?> = mapper.readValue(json,Map::class.java) as Map<String,Any?>
    private fun EventEntity.toResponse()=EventResponse(requireNotNull(id),name,type,startsAt,endsAt,timezone,location,estimatedCapacity,budget,description,status,reentryAllowed)
}
