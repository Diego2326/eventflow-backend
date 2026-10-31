package com.eventflow.eventflow_api.support

import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleDependencyRepository
import org.springframework.stereotype.Component
import java.util.UUID

/** Fixture setup only: enable the persisted dependency graph through the real service. */
@Component
class ModuleTestFixtures(
    private val events: EventService,
    private val catalog: ModuleCatalogRepository,
    private val dependencies: ModuleDependencyRepository
) {
    fun configure(owner: UUID, eventId: UUID, code: String, request: ConfigureModuleRequest = ConfigureModuleRequest()) {
        val visiting = mutableSetOf<String>()
        fun enableDependencies(module: String) {
            check(visiting.add(module)) { "Cyclic module dependency in test catalog" }
            dependencies.findAllByModuleCode(module).forEach { dependency ->
                val required = dependency.requiredModuleCode
                if (events.modules(owner, eventId).none { it.code == required }) {
                    enableDependencies(required)
                    ensureCatalog(required)
                    // Setup-only prerequisites are not guest screens under test.
                    events.configureModule(owner, eventId, required,
                        ConfigureModuleRequest(configuration = mapOf("guestVisible" to false)))
                }
            }
            visiting.remove(module)
        }
        if (request.enabled) enableDependencies(code)
        ensureCatalog(code)
        events.configureModule(owner, eventId, code, request)
    }

    private fun ensureCatalog(code: String) {
        if (!catalog.existsById(code)) catalog.save(ModuleCatalog(code, code, "Test", code))
    }

    fun register(entry: ModuleCatalog) {
        if (!catalog.existsById(entry.code)) catalog.save(entry)
    }
}
