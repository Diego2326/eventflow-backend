package com.eventflow.eventflow_api.auth.infrastructure.messaging

import com.eventflow.eventflow_api.auth.application.AccountMessageSender

import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
class SpringMailAccountMessageSender(private val mailSenderProvider: ObjectProvider<JavaMailSender>) : AccountMessageSender {
    override fun sendLink(to: String, subject: String, link: String) {
        mailSenderProvider.ifAvailable?.send(SimpleMailMessage().apply {
            setTo(to)
            setSubject(subject)
            text = "Abre este enlace seguro: $link"
        })
    }
}
