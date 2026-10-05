package com.eventflow.eventflow_api.module.infrastructure.persistence

import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleRecord

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ModuleRecordRepository : JpaRepository<ModuleRecord, UUID>, ModuleRecordRepositoryPort {
    override fun findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId: UUID, moduleCode: String, recordType: String): List<ModuleRecord>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ModuleRecord r where r.id=:id") override fun findLocked(id: UUID): ModuleRecord?
}
