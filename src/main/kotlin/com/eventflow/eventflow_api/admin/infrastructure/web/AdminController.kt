package com.eventflow.eventflow_api.admin.infrastructure.web

import com.eventflow.eventflow_api.admin.application.AdminDecision
import com.eventflow.eventflow_api.admin.application.AdminCollectionResult
import com.eventflow.eventflow_api.admin.application.AdminService
import com.eventflow.eventflow_api.admin.application.AdminUserResponse
import com.eventflow.eventflow_api.admin.application.CatalogUpdate
import com.eventflow.eventflow_api.admin.application.ReportRequest
import com.eventflow.eventflow_api.admin.application.ResolveReportRequest
import com.eventflow.eventflow_api.admin.application.UserStatusRequest
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import com.eventflow.eventflow_api.admin.domain.AuditLog
import com.eventflow.eventflow_api.admin.domain.ModerationReport
import com.eventflow.eventflow_api.admin.domain.RoleRequest
import com.eventflow.eventflow_api.event.domain.EventEntity

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api") class AdminController(private val s:AdminService){
    @PostMapping("/reports") fun report(a:Authentication,@RequestBody r:ReportRequest)=s.report(a.userId(),r)
    @GetMapping("/admin/users") @PreAuthorize("hasRole('ADMIN')") fun users(@RequestParam(required=false) page:Int?,@RequestParam(required=false) size:Int?):ResponseEntity<List<AdminUserResponse>> = collection(s.users(page,size))
    @GetMapping("/admin/events") @PreAuthorize("hasRole('ADMIN')") fun events(@RequestParam(required=false) page:Int?,@RequestParam(required=false) size:Int?):ResponseEntity<List<EventEntity>> = collection(s.events(page,size))
    @GetMapping("/admin/audit") @PreAuthorize("hasRole('ADMIN')") fun audit(@RequestParam(required=false) page:Int?,@RequestParam(required=false) size:Int?):ResponseEntity<List<AuditLog>> = collection(s.audit(page,size))
    @PatchMapping("/admin/users/{id}/status") @PreAuthorize("hasRole('ADMIN')") fun status(a:Authentication,@PathVariable id:UUID,@RequestBody r:UserStatusRequest)=s.userStatus(a.userId(),id,r)
    @GetMapping("/admin/role-requests") @PreAuthorize("hasRole('ADMIN')") fun roles(@RequestParam(required=false) page:Int?,@RequestParam(required=false) size:Int?):ResponseEntity<List<RoleRequest>> = collection(s.roleRequests(page,size))
    @PostMapping("/admin/role-requests/{id}/decision") @PreAuthorize("hasRole('ADMIN')") fun role(a:Authentication,@PathVariable id:UUID,@RequestBody r:AdminDecision)=s.decideRole(a.userId(),id,r)
    @GetMapping("/admin/reports") @PreAuthorize("hasRole('ADMIN')") fun reports(@RequestParam(required=false) page:Int?,@RequestParam(required=false) size:Int?):ResponseEntity<List<ModerationReport>> = collection(s.reports(page,size))
    @PostMapping("/admin/reports/{id}/resolve") @PreAuthorize("hasRole('ADMIN')") fun resolve(a:Authentication,@PathVariable id:UUID,@RequestBody r:ResolveReportRequest)=s.resolve(a.userId(),id,r)
    @PatchMapping("/admin/modules/{code}") @PreAuthorize("hasRole('ADMIN')") fun module(a:Authentication,@PathVariable code:String,@RequestBody r:CatalogUpdate)=s.module(a.userId(),code,r)

    private fun <T> collection(result:AdminCollectionResult<T>):ResponseEntity<List<T>> {
        val headers=HttpHeaders()
        headers.add("X-Total-Count",result.totalElements.toString())
        result.page?.let{headers.add("X-Page",it.toString())}
        result.size?.let{headers.add("X-Page-Size",it.toString())}
        result.totalPages?.let{headers.add("X-Total-Pages",it.toString())}
        result.hasNext?.let{headers.add("X-Has-Next",it.toString())}
        return ResponseEntity(result.items,headers,HttpStatus.OK)
    }
}
