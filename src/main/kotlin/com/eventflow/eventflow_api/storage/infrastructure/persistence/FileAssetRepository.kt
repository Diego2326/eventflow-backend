package com.eventflow.eventflow_api.storage.infrastructure.persistence

import com.eventflow.eventflow_api.storage.application.port.FileAssetRepositoryPort
import com.eventflow.eventflow_api.storage.domain.FileAsset

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FileAssetRepository : JpaRepository<FileAsset, UUID>, FileAssetRepositoryPort { override fun findAllByEventIdAndModuleCodeAndActiveTrue(eventId:UUID,moduleCode:String):List<FileAsset> }
