package com.eventflow.eventflow_api.admin.application.port

import com.eventflow.eventflow_api.admin.domain.ModerationReport
import com.eventflow.eventflow_api.shared.application.port.CrudPort

import java.util.UUID

interface ModerationReportRepositoryPort : CrudPort<ModerationReport, UUID>
