package com.eventflow.eventflow_api.module.application.port

import com.eventflow.eventflow_api.module.domain.ModuleRecord
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID
import java.time.Instant

interface ModuleRecordRepositoryPort : CrudPort<ModuleRecord, UUID> {
    fun findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId: UUID, moduleCode: String, recordType: String): List<ModuleRecord>
    fun findLocked(id: UUID): ModuleRecord?
    fun findAllByModuleCodeAndRecordTypeAndStartsAtBetweenAndStatus(moduleCode:String, recordType:String, from:Instant, to:Instant, status:String): List<ModuleRecord>
}
