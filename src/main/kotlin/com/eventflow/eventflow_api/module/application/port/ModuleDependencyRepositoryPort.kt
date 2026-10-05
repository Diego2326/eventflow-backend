package com.eventflow.eventflow_api.module.application.port

import com.eventflow.eventflow_api.module.domain.ModuleDependency
import com.eventflow.eventflow_api.module.domain.ModuleDependencyId
import com.eventflow.eventflow_api.shared.application.port.CrudPort

interface ModuleDependencyRepositoryPort : CrudPort<ModuleDependency, ModuleDependencyId> {
    fun findAllByModuleCode(moduleCode: String): List<ModuleDependency>
}
