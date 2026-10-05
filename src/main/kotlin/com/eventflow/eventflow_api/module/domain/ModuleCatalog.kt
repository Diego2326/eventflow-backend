package com.eventflow.eventflow_api.module.domain

import jakarta.persistence.*

@Entity @Table(name = "module_catalog")
class ModuleCatalog(
    @Id @Column(name = "module_code") var code: String = "",
    @Column(name = "module_name", nullable = false) var name: String = "",
    @Column(nullable = false) var category: String = "",
    @Column(nullable = false, columnDefinition = "text") var description: String = "",
    @Column(name = "globally_enabled", nullable = false) var globallyEnabled: Boolean = true
)
