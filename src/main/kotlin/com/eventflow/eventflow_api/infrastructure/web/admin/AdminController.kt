package com.eventflow.eventflow_api.infrastructure.web.admin

import com.eventflow.eventflow_api.infrastructure.web.common.*
import com.eventflow.eventflow_api.application.admin.*
import com.eventflow.eventflow_api.domain.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api") class AdminController(private val s:AdminService){
    @PostMapping("/reports") fun report(a:Authentication,@RequestBody r:ReportRequest)=s.report(a.userId(),r)
    @GetMapping("/admin/users") @PreAuthorize("hasRole('ADMIN')") fun users()=s.users()
    @PatchMapping("/admin/users/{id}/status") @PreAuthorize("hasRole('ADMIN')") fun status(a:Authentication,@PathVariable id:UUID,@RequestBody r:UserStatusRequest)=s.userStatus(a.userId(),id,r)
    @GetMapping("/admin/role-requests") @PreAuthorize("hasRole('ADMIN')") fun roles()=s.roleRequests()
    @PostMapping("/admin/role-requests/{id}/decision") @PreAuthorize("hasRole('ADMIN')") fun role(a:Authentication,@PathVariable id:UUID,@RequestBody r:AdminDecision)=s.decideRole(a.userId(),id,r)
    @GetMapping("/admin/reports") @PreAuthorize("hasRole('ADMIN')") fun reports()=s.reports()
    @PostMapping("/admin/reports/{id}/resolve") @PreAuthorize("hasRole('ADMIN')") fun resolve(a:Authentication,@PathVariable id:UUID,@RequestBody r:ResolveReportRequest)=s.resolve(a.userId(),id,r)
    @PatchMapping("/admin/modules/{code}") @PreAuthorize("hasRole('ADMIN')") fun module(a:Authentication,@PathVariable code:String,@RequestBody r:CatalogUpdate)=s.module(a.userId(),code,r)
}
