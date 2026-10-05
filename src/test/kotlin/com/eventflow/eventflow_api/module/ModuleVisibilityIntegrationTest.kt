package com.eventflow.eventflow_api.module

import com.eventflow.eventflow_api.auth.domain.User
import com.eventflow.eventflow_api.auth.infrastructure.persistence.UserRepository
import com.eventflow.eventflow_api.event.application.ConfigureModuleRequest
import com.eventflow.eventflow_api.event.application.CreateEventRequest
import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.application.CreateInvitationRequest
import com.eventflow.eventflow_api.invitation.application.CheckRequest
import com.eventflow.eventflow_api.invitation.application.InvitationService
import com.eventflow.eventflow_api.invitation.application.QrCheckRequest
import com.eventflow.eventflow_api.invitation.application.RsvpRequest
import com.eventflow.eventflow_api.invitation.application.SeatAssignmentRequest
import com.eventflow.eventflow_api.invitation.domain.Invitation
import com.eventflow.eventflow_api.invitation.infrastructure.persistence.InvitationRepository
import com.eventflow.eventflow_api.module.application.ModuleDataService
import com.eventflow.eventflow_api.module.application.ModuleRecordUpdate
import com.eventflow.eventflow_api.module.domain.ModuleAction
import com.eventflow.eventflow_api.module.domain.ModuleCatalog
import com.eventflow.eventflow_api.module.domain.ModuleRecord
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleActionRepository
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleCatalogRepository
import com.eventflow.eventflow_api.module.infrastructure.persistence.ModuleRecordRepository
import com.eventflow.eventflow_api.notification.domain.NotificationEntity
import com.eventflow.eventflow_api.notification.infrastructure.persistence.NotificationRepository
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.storage.application.StorageService
import com.eventflow.eventflow_api.storage.domain.FileAsset
import com.eventflow.eventflow_api.storage.domain.FileModerationStatus
import com.eventflow.eventflow_api.storage.infrastructure.persistence.FileAssetRepository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

@SpringBootTest
class ModuleVisibilityIntegrationTest {
    @Autowired lateinit var events: EventService
    @Autowired lateinit var modules: ModuleDataService
    @Autowired lateinit var catalog: ModuleCatalogRepository
    @Autowired lateinit var records: ModuleRecordRepository
    @Autowired lateinit var actions: ModuleActionRepository
    @Autowired lateinit var invitations: InvitationRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var files: FileAssetRepository
    @Autowired lateinit var storage: StorageService
    @Autowired lateinit var invitationService: InvitationService
    @Autowired lateinit var notifications:NotificationRepository

    @Test fun `unlinked invitation only sees matching notifications`() {
        val owner=requireNotNull(users.save(User(name="Notice owner",email="notice-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
        val eventId=events.create(owner,CreateEventRequest("Notices","CUSTOM",Instant.now().plusSeconds(3600))).id
        listOf("INV","NOT").forEach{code->catalog.save(ModuleCatalog(code,code,"Core",code));events.configureModule(owner,eventId,code,ConfigureModuleRequest())}
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        val invitation=invitationService.create(owner,eventId,CreateInvitationRequest("Guest"))
        notifications.save(NotificationEntity(eventId=eventId,title="Private table",body="Only A",audienceType="TABLE",audienceValue="A"))
        notifications.save(NotificationEntity(eventId=eventId,title="Public",body="Everyone",audienceType="ALL"))
        assertEquals(listOf("Public"),invitationService.experience(requireNotNull(invitation.token)).notifications.map{it.title})
    }

    @Test fun `QR check in requires the invitation credential`() {
        val owner = requireNotNull(users.save(com.eventflow.eventflow_api.auth.domain.User(
            name = "QR Owner", email = "qr-${UUID.randomUUID()}@example.com", passwordHash = "unused"
        )).id)
        val eventId = events.create(owner, CreateEventRequest("QR", "CUSTOM", Instant.now().plusSeconds(3600))).id
        listOf("INV", "GST").forEach { code ->
            catalog.save(ModuleCatalog(code, code, "Core", code))
            events.configureModule(owner, eventId, code, ConfigureModuleRequest())
        }
        events.transition(owner, eventId, EventStatus.PUBLISHED)
        val created = invitationService.create(owner, eventId, CreateInvitationRequest("Guest"))
        invitationService.rsvp(requireNotNull(created.token), RsvpRequest(true))
        assertThrows(BadRequestException::class.java) {
            invitationService.checkQr(owner, eventId, QrCheckRequest("eventflow:invite:${created.id}"), true)
        }
        assertEquals(1, invitationService.checkQr(owner, eventId, QrCheckRequest(requireNotNull(created.qrPayload)), true).checkedIn)
    }

    @Test fun `group check in tracks each member without mixing aggregate counts`() {
        val owner=requireNotNull(users.save(User(name="Group owner",email="group-${UUID.randomUUID()}@example.com",passwordHash="unused")).id)
        val eventId=events.create(owner,CreateEventRequest("Group","CUSTOM",Instant.now().plusSeconds(3600))).id
        listOf("INV","GST").forEach{code->catalog.save(ModuleCatalog(code,code,"Core",code));events.configureModule(owner,eventId,code,ConfigureModuleRequest())}
        events.transition(owner,eventId,EventStatus.PUBLISHED)
        val invitation=invitationService.create(owner,eventId,CreateInvitationRequest("Lead",allowedCapacity=2))
        invitationService.rsvp(requireNotNull(invitation.token),RsvpRequest(true,listOf("Companion")))
        val entered=invitationService.check(owner,eventId,invitation.id,CheckRequest(memberIndex=1),true)
        assertEquals(1,entered.checkedIn)
        assertEquals(false,entered.members[0].inside)
        assertEquals(true,entered.members[1].inside)
        assertThrows(ConflictException::class.java){invitationService.check(owner,eventId,invitation.id,CheckRequest(memberIndex=1),true)}
        assertThrows(ConflictException::class.java){invitationService.check(owner,eventId,invitation.id,CheckRequest(quantity=1),true)}
    }

    @Test fun `seating area capacity and seat uniqueness are enforced`() {
        val owner = requireNotNull(users.save(com.eventflow.eventflow_api.auth.domain.User(
            name = "Seat Owner", email = "seat-${UUID.randomUUID()}@example.com", passwordHash = "unused"
        )).id)
        val eventId = events.create(owner, CreateEventRequest("Seating", "CUSTOM", Instant.now().plusSeconds(3600))).id
        listOf("INV", "GST").forEach { code ->
            catalog.save(ModuleCatalog(code, code, "Core", code))
            events.configureModule(owner, eventId, code, ConfigureModuleRequest())
        }
        val area = records.save(ModuleRecord(eventId = eventId, moduleCode = "GST", recordType = "SEATING_AREA", title = "Mesa A", capacity = 2))
        val first = invitationService.create(owner, eventId, CreateInvitationRequest("First", allowedCapacity = 2, table = "Mesa A", seat = "A1"))
        assertThrows(ConflictException::class.java) { modules.update(owner,eventId,requireNotNull(area.id),ModuleRecordUpdate(capacity=1)) }
        assertThrows(ConflictException::class.java) {
            invitationService.create(owner, eventId, CreateInvitationRequest("Second", table = "Mesa A", seat = "A2"))
        }
        val second = invitationService.create(owner, eventId, CreateInvitationRequest("Second"))
        assertThrows(ConflictException::class.java) {
            invitationService.assignSeat(owner, eventId, second.id, SeatAssignmentRequest("Mesa A", "A1"))
        }
        invitationService.revoke(owner, eventId, first.id)
        assertEquals("Mesa A", invitationService.assignSeat(owner, eventId, second.id, SeatAssignmentRequest("Mesa A", "A1")).table)
    }

    @Test fun `direct record and action endpoints honor guest visibility`() {
        val owner = requireNotNull(users.save(com.eventflow.eventflow_api.auth.domain.User(
            name = "Owner", email = "owner-${UUID.randomUUID()}@example.com", passwordHash = "unused"
        )).id)
        val guest = requireNotNull(users.save(com.eventflow.eventflow_api.auth.domain.User(
            name = "Guest", email = "guest-${UUID.randomUUID()}@example.com", passwordHash = "unused"
        )).id)
        val otherGuest = requireNotNull(users.save(com.eventflow.eventflow_api.auth.domain.User(
            name = "Other", email = "other-${UUID.randomUUID()}@example.com", passwordHash = "unused"
        )).id)
        val eventId = events.create(owner, CreateEventRequest("Visible", "CUSTOM", Instant.now().plusSeconds(3600))).id
        listOf("GAL", "RSC", "ORD").forEach { code ->
            catalog.save(ModuleCatalog(code, code, "Contenido", code))
            events.configureModule(owner, eventId, code, ConfigureModuleRequest())
        }
        listOf(guest, otherGuest).forEach { userId ->
            invitations.save(Invitation(eventId = eventId, linkedUserId = userId,
                guestName = "Guest", tokenHash = UUID.randomUUID().toString().replace("-", "")))
        }
        val pending = records.save(ModuleRecord(eventId = eventId, moduleCode = "GAL",
            recordType = "PHOTO", ownerUserId = guest, status = "PENDING"))
        val order = records.save(ModuleRecord(eventId = eventId, moduleCode = "ORD",
            recordType = "ORDER", ownerUserId = guest))
        val certificate = records.save(ModuleRecord(eventId = eventId, moduleCode = "RSC",
            recordType = "CERTIFICATE", payload = "{\"recipientUserId\":\"$guest\"}"))
        val orderId = requireNotNull(order.id)
        actions.save(ModuleAction(moduleRecordId = orderId, actorUserId = guest, actionType = "SAVE"))

        assertThrows(NotFoundException::class.java) { modules.get(otherGuest, eventId, requireNotNull(pending.id)) }
        assertThrows(NotFoundException::class.java) { modules.get(otherGuest, eventId, orderId) }
        assertThrows(NotFoundException::class.java) { modules.get(otherGuest, eventId, requireNotNull(certificate.id)) }
        assertThrows(NotFoundException::class.java) { modules.actionList(otherGuest, eventId, orderId) }
        assertEquals(1, modules.actionList(guest, eventId, orderId).size)
        assertEquals(1, modules.actionList(owner, eventId, orderId).size)

        val pendingFile = files.save(FileAsset(eventId = eventId, uploaderUserId = guest,
            moduleCode = "GAL", objectPath = "test/${UUID.randomUUID()}",
            originalName = "photo.jpg", contentType = "image/jpeg", sizeBytes = 3,
            moderationStatus = FileModerationStatus.PENDING))
        assertEquals(0, storage.list(otherGuest, eventId, "GAL").size)
        assertEquals(1, storage.list(guest, eventId, "GAL").size)
        storage.moderate(owner, eventId, requireNotNull(pendingFile.id), FileModerationStatus.ACTIVE)
        assertEquals(1, storage.list(otherGuest, eventId, "GAL").size)

        val linked = invitations.findAllByLinkedUserId(otherGuest).single()
        linked.revokedAt = Instant.now()
        invitations.save(linked)
        assertThrows(ForbiddenException::class.java) { events.get(otherGuest, eventId) }
        assertEquals(0, invitationService.mine(otherGuest).size)
    }
}
