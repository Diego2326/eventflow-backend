package com.eventflow.eventflow_api.admin.infrastructure.persistence

import com.eventflow.eventflow_api.admin.application.port.ModerationReportRepositoryPort
import com.eventflow.eventflow_api.admin.domain.ModerationReport

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ModerationReportRepository : JpaRepository<ModerationReport, UUID>, ModerationReportRepositoryPort
