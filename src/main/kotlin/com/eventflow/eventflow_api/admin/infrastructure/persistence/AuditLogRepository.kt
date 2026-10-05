package com.eventflow.eventflow_api.admin.infrastructure.persistence

import com.eventflow.eventflow_api.admin.application.port.AuditLogRepositoryPort
import com.eventflow.eventflow_api.admin.domain.AuditLog

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID>, AuditLogRepositoryPort
