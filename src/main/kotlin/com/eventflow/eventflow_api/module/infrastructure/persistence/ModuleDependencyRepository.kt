package com.eventflow.eventflow_api.module.infrastructure.persistence

import com.eventflow.eventflow_api.module.application.port.ModuleDependencyRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleDependency
import com.eventflow.eventflow_api.module.domain.ModuleDependencyId

import org.springframework.data.jpa.repository.JpaRepository

interface ModuleDependencyRepository : JpaRepository<ModuleDependency, ModuleDependencyId>, ModuleDependencyRepositoryPort { override fun findAllByModuleCode(moduleCode: String): List<ModuleDependency> }
