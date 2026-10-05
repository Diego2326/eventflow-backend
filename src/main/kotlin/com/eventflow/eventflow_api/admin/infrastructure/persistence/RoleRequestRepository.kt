package com.eventflow.eventflow_api.admin.infrastructure.persistence

import com.eventflow.eventflow_api.admin.application.port.RoleRequestRepositoryPort
import com.eventflow.eventflow_api.admin.domain.RoleRequest

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoleRequestRepository : JpaRepository<RoleRequest, UUID>, RoleRequestRepositoryPort
