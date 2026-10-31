package com.eventflow.eventflow_api.admin.infrastructure.config

import com.eventflow.eventflow_api.auth.domain.UserStatus
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

/** Refuse production startup before the web server starts accepting requests. */
@Component
@Profile("prod", "production")
class ProductionDemoAccountGuard(private val users: UserRepository) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        val demoAccounts = listOf(
            "11111111-1111-4111-8111-111111111111" to "demo.organizador@eventflow.gt",
            "22222222-2222-4222-8222-222222222222" to "demo.invitada@eventflow.gt",
            "33333333-3333-4333-8333-333333333333" to "demo.proveedor@eventflow.gt"
        )
        check(demoAccounts.none { (id, email) ->
            val byId = users.findById(UUID.fromString(id)).orElse(null)
            val byEmail = users.findByEmail(email)
            listOfNotNull(byId, byEmail).any {
                it.status in setOf(UserStatus.ACTIVE, UserStatus.DELETION_PENDING)
            }
        }) {
            "Production startup refused: active demo accounts from V8 must be disabled before deployment. No data was changed by this guard."
        }
    }
}
