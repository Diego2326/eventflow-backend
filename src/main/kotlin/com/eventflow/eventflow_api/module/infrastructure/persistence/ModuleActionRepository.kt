package com.eventflow.eventflow_api.module.infrastructure.persistence

import com.eventflow.eventflow_api.module.application.port.ModuleActionRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleAction

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ModuleActionRepository : JpaRepository<ModuleAction, UUID>, ModuleActionRepositoryPort {
    override fun findAllByModuleRecordIdOrderByCreatedAt(moduleRecordId: UUID): List<ModuleAction>
    override fun findAllByModuleRecordIdAndActorUserIdOrderByCreatedAt(moduleRecordId: UUID, actorUserId: UUID): List<ModuleAction>
    override fun existsByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): Boolean
    override fun findByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): ModuleAction?
}
