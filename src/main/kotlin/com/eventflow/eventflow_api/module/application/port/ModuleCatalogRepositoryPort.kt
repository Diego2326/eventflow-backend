package com.eventflow.eventflow_api.module.application.port

import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.shared.application.port.CrudPort

interface ModuleCatalogRepositoryPort : CrudPort<ModuleCatalog, String>
