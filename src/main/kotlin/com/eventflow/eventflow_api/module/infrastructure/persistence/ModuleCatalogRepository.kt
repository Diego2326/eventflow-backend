package com.eventflow.eventflow_api.module.infrastructure.persistence

import com.eventflow.eventflow_api.module.application.port.ModuleCatalogRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleCatalog

import org.springframework.data.jpa.repository.JpaRepository

interface ModuleCatalogRepository : JpaRepository<ModuleCatalog, String>, ModuleCatalogRepositoryPort
