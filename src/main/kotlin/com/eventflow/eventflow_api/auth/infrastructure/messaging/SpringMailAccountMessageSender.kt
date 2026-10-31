package com.eventflow.eventflow_api.auth.infrastructure.messaging

import com.eventflow.eventflow_api.auth.application.AccountMessageSender

import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Value
import jakarta.annotation.PostConstruct

@Component
class SpringMailAccountMessageSender(
    private val mailSenderProvider: ObjectProvider<JavaMailSender>,
    @Value("\${app.auth.mail-required:false}") private val mailRequired: Boolean,
    @Value("\${spring.mail.host:}") private val mailHost: String,
    @Value("\${app.auth.mail-from:}") private val mailFrom: String,
    @Value("\${app.auth.expose-tokens:false}") private val exposeTokens: Boolean,
    @Value("\${spring.profiles.active:}") private val activeProfiles: String
) : AccountMessageSender {
    @PostConstruct
    fun validateMailConfiguration() {
        val protectedProfile = activeProfiles.split(',').map(String::trim).any { it in setOf("supabase", "prod", "production") }
        if (protectedProfile && (!mailRequired || exposeTokens)) {
            throw IllegalStateException("Protected profiles require mail delivery and must not expose account tokens")
        }
        if (mailRequired && (mailHost.isBlank() || mailFrom.isBlank() || mailSenderProvider.ifAvailable == null)) {
            throw IllegalStateException("Email delivery is required; configure MAIL_HOST, MAIL_FROM and SMTP settings")
        }
    }

    override fun sendLink(to: String, subject: String, link: String) {
        val sender = mailSenderProvider.ifAvailable
        if (sender == null) {
            if (mailRequired) throw IllegalStateException("Email delivery is not configured")
            return
        }
        sender.send(SimpleMailMessage().apply {
            setFrom(mailFrom.ifBlank { null })
            setTo(to)
            setSubject(subject)
            text = "Usa el siguiente enlace seguro. Es de un solo uso y caduca pronto:\n\n$link"
        })
    }
}
