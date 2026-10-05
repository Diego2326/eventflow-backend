package com.eventflow.eventflow_api.storage.application.port

import com.eventflow.eventflow_api.shared.application.port.CrudPort
import com.eventflow.eventflow_api.storage.domain.FileAsset

import java.util.UUID

interface FileAssetRepositoryPort : CrudPort<FileAsset, UUID> {
    fun findAllByEventIdAndModuleCodeAndActiveTrue(eventId: UUID, moduleCode: String): List<FileAsset>
}
