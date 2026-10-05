package com.eventflow.eventflow_api.admin.application.port

import com.eventflow.eventflow_api.admin.domain.AuditLog
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface AuditLogRepositoryPort : CrudPort<AuditLog, UUID>
