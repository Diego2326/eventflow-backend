package com.eventflow.eventflow_api.event.application

import com.eventflow.eventflow_api.assistance.application.port.AssistanceRequestRepositoryPort
import com.eventflow.eventflow_api.assistance.domain.AssistanceStatus
import com.eventflow.eventflow_api.event.application.port.EventCollaboratorRepositoryPort
import com.eventflow.eventflow_api.event.application.port.EventRepositoryPort
import com.eventflow.eventflow_api.event.domain.EventCollaborator
import com.eventflow.eventflow_api.event.domain.EventCollaboratorId
import com.eventflow.eventflow_api.event.domain.EventEntity
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.application.port.GuestAccessLogRepositoryPort
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.invitation.domain.InvitationStatus
import com.eventflow.eventflow_api.marketplace.application.port.ReservationRepositoryPort
import com.eventflow.eventflow_api.module.application.port.EventModuleRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleCatalogRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleDependencyRepositoryPort
import com.eventflow.eventflow_api.module.domain.EventModule
import com.eventflow.eventflow_api.module.domain.EventModuleId
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.port.JsonCodec

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
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
    val checkedIn: Int, val reservations: Int, val pendingAssistance: Int,
    val pendingGuests: Int, val declinedGuests: Int, val checkedOut: Int, val remainingCapacity: Int)
data class CollaboratorRequest(val userId:UUID,val permissions:Set<String>)

@Service
class EventService(
    private val events: EventRepositoryPort, private val collaborators: EventCollaboratorRepositoryPort,
    private val catalog: ModuleCatalogRepositoryPort, private val dependencies: ModuleDependencyRepositoryPort,
    private val eventModules: EventModuleRepositoryPort, private val invitations: InvitationRepositoryPort,
    private val accessLogs: GuestAccessLogRepositoryPort, private val reservations: ReservationRepositoryPort,
    private val assistance: AssistanceRequestRepositoryPort, private val mapper: JsonCodec,
    private val transitionEvent: TransitionEvent, private val createEvent: CreateEvent
) {
    @Transactional
    fun create(userId: UUID, request: CreateEventRequest): EventResponse {
        val id = try {
            createEvent.execute(NewEvent(userId, request.name, request.type, request.startsAt,
                request.endsAt, request.timezone, request.location, request.estimatedCapacity,
                request.budget, request.description, request.reentryAllowed))
        } catch (invalid: InvalidEvent) { throw BadRequestException(requireNotNull(invalid.message)) }
        return events.findById(id).orElseThrow().toResponse()
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID): List<EventResponse> {
        val direct = events.findAccessible(userId)
        val guestEventIds = invitations.findAllByLinkedUserId(userId).filter(::validGuestAccess).map { it.eventId }.toSet()
        val guests = if (guestEventIds.isEmpty()) emptyList() else events.findAllById(guestEventIds)
        return (direct + guests).distinctBy { it.id }.sortedBy { it.startsAt }.map { it.toResponse() }
    }
    @Transactional(readOnly = true) fun get(userId: UUID, eventId: UUID): EventResponse = accessible(userId,eventId).toResponse()
    @Transactional fun update(userId: UUID, eventId: UUID, r: UpdateEventRequest): EventResponse {
        val e=owned(userId,eventId); val start=r.startsAt?:e.startsAt; val end=r.endsAt?:e.endsAt; validateDates(start,end)
        if(e.status in setOf(EventStatus.FINISHED,EventStatus.CANCELLED))throw ConflictException("El evento ya está cerrado")
        r.name?.let { if(it.isBlank()) throw BadRequestException("El nombre es obligatorio") else e.name=it.trim() }
        e.startsAt=start; e.endsAt=end; r.timezone?.let{if(runCatching{ZoneId.of(it)}.isFailure)throw BadRequestException("Zona horaria inválida");e.timezone=it}; r.location?.let{e.location=it}; r.estimatedCapacity?.let{if(it<1)throw BadRequestException("Capacidad inválida");e.estimatedCapacity=it}
        r.budget?.let{if(it<BigDecimal.ZERO)throw BadRequestException("Presupuesto inválido");e.budget=it}; r.description?.let{e.description=it}; r.reentryAllowed?.let{e.reentryAllowed=it}; e.updatedAt=Instant.now(); return e.toResponse()
    }
    @Transactional fun transition(userId: UUID,eventId: UUID,status: EventStatus): EventResponse {
        val e=owned(userId,eventId)
        try { transitionEvent.execute(eventId,e.status,status) }
        catch (failure: EventTransitionFailure) { throw ConflictException(requireNotNull(failure.message)) }
        e.status=status; return e.toResponse()
    }

    @Transactional(readOnly = true) fun catalog()=catalog.findAll().filter{it.globallyEnabled}.map{ModuleResponse(it.code,it.name,it.category,it.description)}
    @Transactional(readOnly = true) fun modules(userId: UUID,eventId: UUID): List<ModuleResponse> {
        accessible(userId,eventId)
        val manager=try{owned(userId,eventId);true}catch(_:ForbiddenException){false}
        return eventModules.findAllByEventIdOrderByDisplayOrder(eventId).filter{it.enabled&&(manager||readMap(it.configuration)["guestVisible"]!=false)}.mapNotNull { em -> catalog.findById(em.moduleCode).orElse(null)?.takeIf{it.globallyEnabled}?.let { c ->
            ModuleResponse(c.code,c.name,c.category,c.description,true,em.displayOrder,em.featured,readMap(em.configuration)) } }
    }
    @Transactional fun configureModule(userId: UUID,eventId: UUID,codeRaw:String,r:ConfigureModuleRequest): ModuleResponse {
        owned(userId,eventId); val code=codeRaw.uppercase(); val c=catalog.findById(code).orElseThrow{NotFoundException("Módulo no encontrado")}
        if(!c.globallyEnabled) throw ConflictException("El módulo está deshabilitado globalmente")
        if(r.featured && !r.enabled) throw BadRequestException("Un módulo destacado debe estar habilitado")
        validateConfiguration(code,r.configuration)
        if(r.enabled) dependencies.findAllByModuleCode(code).forEach { dep ->
            val active=eventModules.findById(EventModuleId(eventId,dep.requiredModuleCode)).map{it.enabled}.orElse(false)
            if(!active) throw ConflictException("$code requiere el módulo ${dep.requiredModuleCode}")
        }
        if(!r.enabled) {
            val dependents=dependencies.findAll().filter{it.requiredModuleCode==code}.map{it.moduleCode}
            if(eventModules.findAllByEventIdOrderByDisplayOrder(eventId).any{it.enabled && it.moduleCode in dependents}) throw ConflictException("Otros módulos activos dependen de $code")
        }
        val em=eventModules.findById(EventModuleId(eventId,code)).orElse(EventModule(eventId,code))
        em.enabled=r.enabled; em.displayOrder=r.order; em.featured=r.featured; em.configuration=mapper.write(r.configuration); em.updatedAt=Instant.now(); eventModules.save(em)
        return ModuleResponse(c.code,c.name,c.category,c.description,em.enabled,em.displayOrder,em.featured,r.configuration)
    }
    @Transactional(readOnly = true) fun dashboard(userId:UUID,eventId:UUID):DashboardResponse {
        val e=owned(userId,eventId)
        val inv=invitations.findAllByEventId(eventId).filter{it.revokedAt==null}
        val ids=inv.map{requireNotNull(it.id)}
        val logs=if(ids.isEmpty()) emptyList() else accessLogs.findAllByInvitationIdIn(ids)
        val checked=logs.groupBy{it.invitationId}.values.sumOf{history->history.sumOf{if(it.action=="CHECK_IN")it.quantity else -it.quantity}.coerceAtLeast(0)}
        val out=logs.filter{it.action=="CHECK_OUT"}.sumOf{it.quantity}
        return DashboardResponse(e.toResponse(),eventModules.findAllByEventIdOrderByDisplayOrder(eventId).count{it.enabled},inv.size,
            inv.count{it.status==InvitationStatus.ACCEPTED},checked,reservations.findAllByEventId(eventId).size,
            assistance.findAllByEventIdOrderByPriorityDescCreatedAtAsc(eventId).count{it.status !in setOf(AssistanceStatus.ATTENDED,AssistanceStatus.CANCELLED)},
            inv.count{it.status==InvitationStatus.PENDING},inv.count{it.status==InvitationStatus.DECLINED},out,
            (inv.filter{it.status==InvitationStatus.ACCEPTED}.sumOf{it.allowedCapacity}-checked).coerceAtLeast(0))
    }
    @Transactional fun addCollaborator(userId:UUID,eventId:UUID,r:CollaboratorRequest):EventCollaborator{ownerOnly(userId,eventId);if(r.userId==userId)throw BadRequestException("El propietario ya administra el evento");if(r.permissions.isEmpty()||r.permissions.any{it.isBlank()||',' in it})throw BadRequestException("Selecciona permisos válidos");return collaborators.save(EventCollaborator(eventId,r.userId,r.permissions.map{it.trim().uppercase()}.joinToString(",")))}
    @Transactional(readOnly=true) fun listCollaborators(userId:UUID,eventId:UUID):List<EventCollaborator>{ownerOnly(userId,eventId);return collaborators.findAllByEventId(eventId)}
    @Transactional fun removeCollaborator(userId:UUID,eventId:UUID,collaboratorId:UUID){ownerOnly(userId,eventId);collaborators.deleteById(EventCollaboratorId(eventId,collaboratorId))}
    fun ownerOnly(userId:UUID,eventId:UUID):EventEntity {val e=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(e.ownerUserId!=userId)throw ForbiddenException("Solo el propietario puede realizar esta operación");return e}
    fun owned(userId:UUID,eventId:UUID):EventEntity=authorized(userId,eventId,"ALL")
    fun authorized(userId:UUID,eventId:UUID,permission:String):EventEntity{val e=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")};if(e.ownerUserId==userId)return e;val c=collaborators.findByEventIdAndUserId(eventId,userId)?:throw ForbiddenException("No tienes acceso a este evento");val p=c.permissions.split(',').map{it.trim().uppercase()};if("ALL" !in p&&permission.uppercase() !in p)throw ForbiddenException("No tienes el permiso $permission");return e}
    fun assistanceCategories(userId:UUID,eventId:UUID):Set<String>? {
        val event=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")}
        if(event.ownerUserId==userId)return null
        val collaborator=collaborators.findByEventIdAndUserId(eventId,userId)?:throw ForbiddenException("No tienes acceso a las solicitudes")
        val permissions=collaborator.permissions.split(',').map{it.trim().uppercase()}
        if("ALL" in permissions||"ASSISTANCE" in permissions)return null
        val categories=permissions.filter{it.startsWith("ASSISTANCE:")}.map{it.substringAfter(':')}.toSet()
        if(categories.isEmpty())throw ForbiddenException("No tienes acceso a las solicitudes")
        return categories
    }
    fun accessible(userId:UUID,eventId:UUID):EventEntity { val e=events.findById(eventId).orElseThrow{NotFoundException("Evento no encontrado")}; if(e.ownerUserId!=userId && !collaborators.existsByEventIdAndUserId(eventId,userId) && invitations.findAllByLinkedUserId(userId).none{it.eventId==eventId&&validGuestAccess(it)}) throw ForbiddenException("No tienes acceso a este evento"); return e }
    private fun validGuestAccess(invitation:Invitation):Boolean = invitation.revokedAt==null && invitation.tokenExpiresAt?.isAfter(Instant.now())!=false
    fun requireModule(eventId: UUID, code: String) { if(!eventModules.findById(EventModuleId(eventId,code)).map{it.enabled}.orElse(false)||!catalog.findById(code).map{it.globallyEnabled}.orElse(false)) throw ConflictException("El módulo $code no está habilitado") }
    fun requireVisibleModule(userId:UUID,eventId:UUID,code:String) {
        accessible(userId,eventId)
        requireModule(eventId,code)
        val manager=try { owned(userId,eventId);true } catch (_:ForbiddenException) { false }
        if(!manager&&!guestModuleVisible(eventId,code))throw ForbiddenException("El módulo no está disponible para invitados")
    }
    fun requireAssistanceCategory(eventId:UUID,category:String) {
        requireModule(eventId,"AST")
        val configuration=eventModules.findById(EventModuleId(eventId,"AST")).map{readMap(it.configuration)}.orElse(emptyMap())
        val allowed=(configuration["categories"] as? List<*>)?.map{it.toString().uppercase()}
        if(allowed!=null&&category.uppercase() !in allowed)throw ConflictException("La categoría no está habilitada")
    }
    private fun validateConfiguration(code:String,config:Map<String,Any?>) {
        listOf("guestVisible","moderationRequired","rsvpOpen","allowGuestUploads").forEach { key ->
            if(key in config && config[key] !is Boolean)throw BadRequestException("$key debe ser verdadero o falso")
        }
        if(code=="INV")config["rsvpClosesAt"]?.let { value ->
            try { Instant.parse(value.toString()) } catch (_:Exception) { throw BadRequestException("rsvpClosesAt debe ser una fecha ISO-8601") }
        }
        if(code=="AST")config["categories"]?.let { value ->
            if(value !is List<*>||value.any{it !is String||it.isBlank()})throw BadRequestException("categories debe contener categorías válidas")
        }
    }
    fun guestUploadsAllowed(eventId:UUID,module:String):Boolean {
        requireModule(eventId,module)
        return eventModules.findById(EventModuleId(eventId,module)).map{readMap(it.configuration)["allowGuestUploads"]==true}.orElse(false)
    }
    fun guestModuleVisible(eventId:UUID,module:String):Boolean {
        requireModule(eventId,module)
        return eventModules.findById(EventModuleId(eventId,module)).map{readMap(it.configuration)["guestVisible"]!=false}.orElse(false)
    }
    fun moderationRequired(eventId:UUID,module:String):Boolean {
        requireModule(eventId,module)
        return eventModules.findById(EventModuleId(eventId,module)).map{readMap(it.configuration)["moderationRequired"]!=false}.orElse(true)
    }
    private fun validateDates(start:Instant,end:Instant?){if(end!=null&&!end.isAfter(start))throw BadRequestException("La fecha de fin debe ser posterior al inicio")}
    @Suppress("UNCHECKED_CAST") private fun readMap(json:String):Map<String,Any?> = mapper.readMap(json)
    private fun EventEntity.toResponse()=EventResponse(requireNotNull(id),name,type,startsAt,endsAt,timezone,location,estimatedCapacity,budget,description,status,reentryAllowed)
}
