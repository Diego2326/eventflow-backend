package com.eventflow.eventflow_api.module.domain

import jakarta.persistence.*
import java.io.Serializable

@Entity @Table(name = "module_dependency") @IdClass(ModuleDependencyId::class)
class ModuleDependency(
    @Id @Column(name = "module_code") var moduleCode: String = "",
    @Id @Column(name = "required_module_code") var requiredModuleCode: String = ""
)
data class ModuleDependencyId(var moduleCode: String? = null, var requiredModuleCode: String? = null) : Serializable
