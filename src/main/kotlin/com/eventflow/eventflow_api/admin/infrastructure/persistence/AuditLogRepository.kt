package com.eventflow.eventflow_api.admin.infrastructure.persistence

import com.eventflow.eventflow_api.admin.application.port.AuditLogRepositoryPort
import com.eventflow.eventflow_api.admin.domain.AuditLog

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID>, AuditLogRepositoryPort {
    @Query(value = "select * from audit_log order by created_at desc,audit_log_id desc limit :limit offset :offset", nativeQuery = true)
    override fun findPage(@Param("offset") offset:Int,@Param("limit") limit:Int):List<AuditLog>
    @Query(value = "select count(*) from audit_log", nativeQuery = true)
    override fun countAll():Long
}
