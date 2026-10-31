package com.eventflow.eventflow_api.admin.infrastructure.persistence

import com.eventflow.eventflow_api.admin.application.port.ModerationReportRepositoryPort
import com.eventflow.eventflow_api.admin.domain.ModerationReport

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ModerationReportRepository : JpaRepository<ModerationReport, UUID>, ModerationReportRepositoryPort {
    @Query(value = "select * from moderation_report order by created_at desc,report_id desc limit :limit offset :offset", nativeQuery = true)
    override fun findPage(@Param("offset") offset:Int,@Param("limit") limit:Int):List<ModerationReport>
    @Query(value = "select count(*) from moderation_report", nativeQuery = true)
    override fun countAll():Long
}
