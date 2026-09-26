package com.eventflow.eventflow_api.config

import com.eventflow.eventflow_api.auth.model.Role
import com.eventflow.eventflow_api.auth.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminBootstrap(private val users:UserRepository,@Value("\${app.bootstrap-admin-email:}") private val email:String):ApplicationRunner{
    @Transactional override fun run(args:ApplicationArguments){if(email.isNotBlank())users.findByEmail(email.trim().lowercase())?.roles?.add(Role.ADMIN)}
}
