package com.eventflow.eventflow_api.admin.application

import com.eventflow.eventflow_api.admin.application.port.AuditLogRepositoryPort
import com.eventflow.eventflow_api.admin.application.port.ModerationReportRepositoryPort
import com.eventflow.eventflow_api.admin.application.port.RoleRequestRepositoryPort
import com.eventflow.eventflow_api.admin.domain.AuditLog
import com.eventflow.eventflow_api.admin.domain.ModerationReport
import com.eventflow.eventflow_api.admin.domain.RoleRequest
import com.eventflow.eventflow_api.auth.application.port.UserRepositoryPort
import com.eventflow.eventflow_api.auth.application.port.UserSessionRepositoryPort
import com.eventflow.eventflow_api.auth.domain.Role
import com.eventflow.eventflow_api.auth.domain.UserStatus
import com.eventflow.eventflow_api.module.application.port.ModuleCatalogRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AdminDecision(val approved:Boolean,val reason:String?=null)
data class ReportRequest(val eventId:UUID?=null,val targetType:String,val targetId:UUID,val reason:String)
data class ResolveReportRequest(val status:String,val resolution:String)
data class UserStatusRequest(val status:UserStatus)
data class CatalogUpdate(val enabled:Boolean,val name:String?=null,val description:String?=null,val category:String?=null)
data class AdminUserResponse(val id:UUID,val name:String,val email:String,val status:UserStatus,val roles:Set<Role>,val createdAt:Instant)

@Service class AdminService(private val users:UserRepositoryPort,private val sessions:UserSessionRepositoryPort,private val roleRequests:RoleRequestRepositoryPort,private val reports:ModerationReportRepositoryPort,private val catalog:ModuleCatalogRepositoryPort,private val audits:AuditLogRepositoryPort){
    @Transactional(readOnly=true) fun users()=users.findAll().map{AdminUserResponse(requireNotNull(it.id),it.name,it.email,it.status,it.roles,it.createdAt)}
    @Transactional fun userStatus(admin:UUID,id:UUID,r:UserStatusRequest)=users.findById(id).orElseThrow{NotFoundException("Usuario no encontrado")}.also{
        if(r.status !in setOf(UserStatus.ACTIVE,UserStatus.DISABLED)||it.status==UserStatus.DELETED)throw BadRequestException("Cambio de estado no permitido")
        if(id==admin&&r.status==UserStatus.DISABLED)throw ConflictException("No puedes desactivar tu cuenta administradora")
        if(r.status==UserStatus.ACTIVE&&it.emailVerifiedAt==null)throw ConflictException("El correo de la cuenta no está verificado")
        it.status=r.status;it.updatedAt=Instant.now()
        if(r.status==UserStatus.DISABLED)sessions.revokeAll(id,Instant.now())
        audits.save(AuditLog(actorUserId=admin,action="USER_STATUS_${r.status}",targetType="USER",targetId=id))
    }
    @Transactional(readOnly=true) fun roleRequests()=roleRequests.findAll().filter{it.status=="PENDING"}
    @Transactional fun decideRole(admin:UUID,id:UUID,r:AdminDecision):RoleRequest{val q=roleRequests.findById(id).orElseThrow{NotFoundException("Solicitud no encontrada")};if(q.status!="PENDING")throw ConflictException("La solicitud ya fue decidida");if(r.approved){val u=users.findById(q.userId).orElseThrow{NotFoundException("Usuario no encontrado")};if(u.status!=UserStatus.ACTIVE)throw ConflictException("La cuenta solicitante no está activa");val role=runCatching{Role.valueOf(q.requestedRole)}.getOrNull()?:throw BadRequestException("Rol inválido");if(role in setOf(Role.ADMIN,Role.USER))throw BadRequestException("Rol no aprobable");u.roles.add(role)};q.status=if(r.approved)"APPROVED" else "REJECTED";q.decidedBy=admin;q.decidedAt=Instant.now();q.reason=r.reason;audits.save(AuditLog(actorUserId=admin,action="ROLE_${q.status}",targetType="ROLE_REQUEST",targetId=id));return q}
    @Transactional fun report(userId:UUID,r:ReportRequest):ModerationReport{if(r.reason.isBlank())throw BadRequestException("El motivo es obligatorio");return reports.save(ModerationReport(reporterUserId=userId,eventId=r.eventId,targetType=r.targetType.uppercase(),targetId=r.targetId,reason=r.reason))}
    @Transactional(readOnly=true) fun reports()=reports.findAll()
    @Transactional fun resolve(admin:UUID,id:UUID,r:ResolveReportRequest):ModerationReport{val report=reports.findById(id).orElseThrow{NotFoundException("Reporte no encontrado")};report.status=r.status.uppercase();report.resolution=r.resolution;report.resolvedBy=admin;report.resolvedAt=Instant.now();audits.save(AuditLog(actorUserId=admin,eventId=report.eventId,action="REPORT_${report.status}",targetType=report.targetType,targetId=report.targetId));return report}
    @Transactional fun module(admin:UUID,code:String,r:CatalogUpdate):ModuleCatalog{val m=catalog.findById(code.uppercase()).orElseThrow{NotFoundException("Módulo no encontrado")};m.globallyEnabled=r.enabled;r.name?.let{m.name=it};r.description?.let{m.description=it};r.category?.let{m.category=it};audits.save(AuditLog(actorUserId=admin,action="MODULE_CATALOG_UPDATE",targetType="MODULE",details="{\"code\":\"${m.code}\"}"));return m}
}
