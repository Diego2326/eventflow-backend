package com.eventflow.eventflow_api.auth

import com.eventflow.eventflow_api.auth.infrastructure.messaging.SpringMailAccountMessageSender
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class SpringMailAccountMessageSenderTest {
    @Test
    fun `sends one-use deep link through configured Spring mail provider`() {
        val provider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<JavaMailSender>
        val mailer = Mockito.mock(JavaMailSender::class.java)
        Mockito.`when`(provider.ifAvailable).thenReturn(mailer)
        val sender = SpringMailAccountMessageSender(provider, true, "smtp.example", "no-reply@example.com", false, "local")
        sender.validateMailConfiguration()
        sender.sendLink("member@example.com", "Verify EventFlow", "eventflow://auth/verify-email?token=one-use-value")

        val message = ArgumentCaptor.forClass(SimpleMailMessage::class.java)
        Mockito.verify(mailer).send(message.capture())
        assertArrayEquals(arrayOf("member@example.com"), message.value.to)
        assertEquals("no-reply@example.com", message.value.from)
        assertTrue(message.value.text!!.contains("one-use") && message.value.text!!.contains("eventflow://auth/verify-email"))
    }

    @Test
    fun `protected profiles reject development tokens even when explicitly enabled`() {
        val provider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<JavaMailSender>
        val sender = SpringMailAccountMessageSender(provider, true, "smtp.example", "no-reply@example.com", true, "supabase")
        assertThrows(IllegalStateException::class.java) { sender.validateMailConfiguration() }
    }
}
