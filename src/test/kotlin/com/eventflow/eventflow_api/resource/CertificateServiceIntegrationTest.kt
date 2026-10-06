package com.eventflow.eventflow_api.resource

import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.domain.GuestAccessLog
import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.invitation.infrastructure.persistence.GuestAccessLogRepository
import com.eventflow.eventflow_api.invitation.infrastructure.persistence.InvitationRepository
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.ModuleRecordUpdate
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.resource.application.CertificateIssueRequest
import com.eventflow.eventflow_api.resource.application.CertificateService
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.storage.domain.FileAsset
import com.eventflow.eventflow_api.storage.infrastructure.persistence.FileAssetRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest class CertificateServiceIntegrationTest {
    @Autowired lateinit var events:EventService
    @Autowired lateinit var certificates:CertificateService
    @Autowired lateinit var users:UserRepository
    @Autowired lateinit var invitations:InvitationRepository
    @Autowired lateinit var logs:GuestAccessLogRepository
    @Autowired lateinit var files:FileAssetRepository
    @Autowired lateinit var catalog:ModuleCatalogRepository
    @Autowired lateinit var modules:ModuleDataService

    @Test fun `certificate requires attendance and private recipient file`() {
        val owner=user();val guest=user()
        val eventId=events.create(owner,CreateEventRequest("Certificates","CUSTOM",Instant.now().plusSeconds(3600))).id
        catalog.save(ModuleCatalog("RSC","Resources","Guest","Certificates"))
        events.configureModule(owner,eventId,"RSC",ConfigureModuleRequest())
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        events.transition(owner,eventId,EventStatus.RUNNING)
        events.transition(owner,eventId,EventStatus.FINISHED)
        val invitation=invitations.save(Invitation(eventId=eventId,linkedUserId=guest,guestName="Guest",tokenHash=UUID.randomUUID().toString()))
        val file=files.save(FileAsset(eventId=eventId,uploaderUserId=owner,moduleCode="RSC",objectPath="test/${UUID.randomUUID()}",
            originalName="certificate.pdf",contentType="application/pdf",sizeBytes=100,recipientUserId=guest))
        val request=CertificateIssueRequest(guest,requireNotNull(file.id),"Attendance")
        assertThrows(ConflictException::class.java){certificates.issue(owner,eventId,request)}
        logs.save(GuestAccessLog(invitationId=requireNotNull(invitation.id),action="CHECK_IN"))
        val certificate=certificates.issue(owner,eventId,request)
        assertEquals(guest,certificate.recipientUserId)
        assertEquals(listOf(certificate.id),certificates.mine(guest,eventId).map{it.id})
        assertThrows(BadRequestException::class.java){modules.update(owner,eventId,certificate.id,
            ModuleRecordUpdate(payload=mapOf("recipientUserId" to owner.toString(),"fileId" to file.id.toString())))}
        assertThrows(ConflictException::class.java){certificates.issue(owner,eventId,request)}
    }
    private fun user()=requireNotNull(users.save(User(name="Certificate user",email="cert-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
}
