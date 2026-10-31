package com.eventflow.eventflow_api.admin.infrastructure.persistence

import com.eventflow.eventflow_api.admin.application.port.RoleRequestRepositoryPort
import com.eventflow.eventflow_api.admin.domain.RoleRequest

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RoleRequestRepository : JpaRepository<RoleRequest, UUID>, RoleRequestRepositoryPort {
    @Query(value = "select * from role_request where status='PENDING' order by created_at desc,role_request_id desc limit :limit offset :offset", nativeQuery = true)
    override fun findPendingPage(@Param("offset") offset:Int,@Param("limit") limit:Int):List<RoleRequest>
    @Query(value = "select count(*) from role_request where status='PENDING'", nativeQuery = true)
    override fun countPending():Long
}
