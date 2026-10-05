package com.eventflow.eventflow_api.application.admin

import com.eventflow.eventflow_api.application.port.*

import com.eventflow.eventflow_api.auth.model.Role
import com.eventflow.eventflow_api.auth.model.UserStatus
import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
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

@Service class AdminService(private val users:UserRepositoryPort,private val roleRequests:RoleRequestRepositoryPort,private val reports:ModerationReportRepositoryPort,private val catalog:ModuleCatalogRepositoryPort,private val audits:AuditLogRepositoryPort){
    @Transactional(readOnly=true) fun users()=users.findAll().map{AdminUserResponse(requireNotNull(it.id),it.name,it.email,it.status,it.roles,it.createdAt)}
    @Transactional fun userStatus(admin:UUID,id:UUID,r:UserStatusRequest)=users.findById(id).orElseThrow{NotFoundException("Usuario no encontrado")}.also{it.status=r.status;it.updatedAt=Instant.now();audits.save(AuditLog(actorUserId=admin,action="USER_STATUS_${r.status}",targetType="USER",targetId=id))}
    @Transactional(readOnly=true) fun roleRequests()=roleRequests.findAll().filter{it.status=="PENDING"}
    @Transactional fun decideRole(admin:UUID,id:UUID,r:AdminDecision):RoleRequest{val q=roleRequests.findById(id).orElseThrow{NotFoundException("Solicitud no encontrada")};if(q.status!="PENDING")throw ConflictException("La solicitud ya fue decidida");q.status=if(r.approved)"APPROVED" else "REJECTED";q.decidedBy=admin;q.decidedAt=Instant.now();q.reason=r.reason;if(r.approved){val u=users.findById(q.userId).orElseThrow{NotFoundException("Usuario no encontrado")};u.roles.add(Role.valueOf(q.requestedRole))};audits.save(AuditLog(actorUserId=admin,action="ROLE_${q.status}",targetType="ROLE_REQUEST",targetId=id));return q}
    @Transactional fun report(userId:UUID,r:ReportRequest):ModerationReport{if(r.reason.isBlank())throw BadRequestException("El motivo es obligatorio");return reports.save(ModerationReport(reporterUserId=userId,eventId=r.eventId,targetType=r.targetType.uppercase(),targetId=r.targetId,reason=r.reason))}
    @Transactional(readOnly=true) fun reports()=reports.findAll()
    @Transactional fun resolve(admin:UUID,id:UUID,r:ResolveReportRequest):ModerationReport{val report=reports.findById(id).orElseThrow{NotFoundException("Reporte no encontrado")};report.status=r.status.uppercase();report.resolution=r.resolution;report.resolvedBy=admin;report.resolvedAt=Instant.now();audits.save(AuditLog(actorUserId=admin,eventId=report.eventId,action="REPORT_${report.status}",targetType=report.targetType,targetId=report.targetId));return report}
    @Transactional fun module(admin:UUID,code:String,r:CatalogUpdate):ModuleCatalog{val m=catalog.findById(code.uppercase()).orElseThrow{NotFoundException("Módulo no encontrado")};m.globallyEnabled=r.enabled;r.name?.let{m.name=it};r.description?.let{m.description=it};r.category?.let{m.category=it};audits.save(AuditLog(actorUserId=admin,action="MODULE_CATALOG_UPDATE",targetType="MODULE",details="{\"code\":\"${m.code}\"}"));return m}
}

