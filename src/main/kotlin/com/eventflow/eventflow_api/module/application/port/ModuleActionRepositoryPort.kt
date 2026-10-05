package com.eventflow.eventflow_api.module.application.port

import com.eventflow.eventflow_api.module.domain.ModuleAction
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface ModuleActionRepositoryPort : CrudPort<ModuleAction, UUID> {
    fun findAllByModuleRecordIdOrderByCreatedAt(moduleRecordId: UUID): List<ModuleAction>
    fun findAllByModuleRecordIdAndActorUserIdOrderByCreatedAt(moduleRecordId: UUID, actorUserId: UUID): List<ModuleAction>
    fun existsByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): Boolean
    fun findByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): ModuleAction?
    fun findAllByActorUserIdAndActionTypeInOrderByCreatedAt(actorUserId: UUID, actionTypes: Collection<String>): List<ModuleAction>
    fun findAllByModuleRecordIdInAndActionType(moduleRecordIds: Collection<UUID>, actionType: String): List<ModuleAction>
}
