package com.eventflow.eventflow_api.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID

interface EventRepository : JpaRepository<EventEntity, UUID> {
    @Query("select distinct e from EventEntity e left join EventCollaborator c on c.eventId=e.id where e.ownerUserId=:userId or c.userId=:userId")
    fun findAccessible(userId: UUID): List<EventEntity>
}
interface EventCollaboratorRepository : JpaRepository<EventCollaborator, EventCollaboratorId> {
    fun existsByEventIdAndUserId(eventId: UUID, userId: UUID): Boolean
    fun findByEventIdAndUserId(eventId: UUID, userId: UUID): EventCollaborator?
    fun findAllByEventId(eventId: UUID): List<EventCollaborator>
}
interface ModuleCatalogRepository : JpaRepository<ModuleCatalog, String>
interface ModuleDependencyRepository : JpaRepository<ModuleDependency, ModuleDependencyId> { fun findAllByModuleCode(moduleCode: String): List<ModuleDependency> }
interface EventModuleRepository : JpaRepository<EventModule, EventModuleId> { fun findAllByEventIdOrderByDisplayOrder(eventId: UUID): List<EventModule> }
interface MarketplaceOfferingRepository : JpaRepository<MarketplaceOffering, UUID> {
    fun findAllByTypeAndStatus(type: OfferingType, status: OfferingStatus): List<MarketplaceOffering>
    fun findAllByOwnerUserId(ownerUserId: UUID): List<MarketplaceOffering>
}
interface OfferingAvailabilityRepository : JpaRepository<OfferingAvailability, UUID> { fun findAllByOfferingId(offeringId: UUID): List<OfferingAvailability> }
interface ReservationRepository : JpaRepository<Reservation, UUID> {
    fun findAllByEventId(eventId: UUID): List<Reservation>
    @Query("select count(r)>0 from Reservation r where r.offeringId=:offeringId and r.status='ACCEPTED' and r.startsAt<:endsAt and r.endsAt>:startsAt")
    fun hasConflict(offeringId: UUID, startsAt: Instant, endsAt: Instant): Boolean
}
interface SimulatedPaymentRepository : JpaRepository<SimulatedPayment, UUID> { fun findAllByReservationId(reservationId: UUID): List<SimulatedPayment> }
interface InvitationRepository : JpaRepository<Invitation, UUID> {
    fun findByTokenHash(tokenHash: String): Invitation?
    fun findAllByEventId(eventId: UUID): List<Invitation>
    fun findAllByLinkedUserId(linkedUserId: UUID): List<Invitation>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Invitation i where i.id=:id") fun findLocked(id:UUID):Invitation?
}
interface GuestAccessLogRepository : JpaRepository<GuestAccessLog, UUID> {
    fun findAllByInvitationIdOrderByCreatedAt(invitationId: UUID): List<GuestAccessLog>
}
interface AgendaItemRepository : JpaRepository<AgendaItem, UUID> { fun findAllByEventIdOrderByStartsAt(eventId: UUID): List<AgendaItem> }
interface AgendaFavoriteRepository : JpaRepository<AgendaFavorite, AgendaFavoriteId> { fun findAllByUserId(userId: UUID): List<AgendaFavorite> }
interface AssistanceRequestRepository : JpaRepository<AssistanceRequest, UUID> { fun findAllByEventIdOrderByPriorityDescCreatedAtAsc(eventId: UUID): List<AssistanceRequest> }
interface NotificationRepository : JpaRepository<NotificationEntity, UUID> { fun findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId: UUID): List<NotificationEntity> }
interface ConversationMessageRepository : JpaRepository<ConversationMessage, UUID> { fun findAllByEventIdAndChannelOrderByCreatedAt(eventId: UUID, channel: String): List<ConversationMessage> }
interface ModuleRecordRepository : JpaRepository<ModuleRecord, UUID> {
    fun findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId: UUID, moduleCode: String, recordType: String): List<ModuleRecord>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ModuleRecord r where r.id=:id") fun findLocked(id: UUID): ModuleRecord?
}
interface ModuleActionRepository : JpaRepository<ModuleAction, UUID> {
    fun findAllByModuleRecordIdOrderByCreatedAt(moduleRecordId: UUID): List<ModuleAction>
    fun existsByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): Boolean
    fun findByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): ModuleAction?
}
interface ReviewRepository : JpaRepository<Review, UUID> { fun existsByReservationIdAndAuthorUserId(reservationId: UUID, authorUserId: UUID): Boolean }
interface ModerationReportRepository : JpaRepository<ModerationReport, UUID>
interface RoleRequestRepository : JpaRepository<RoleRequest, UUID>
interface AuditLogRepository : JpaRepository<AuditLog, UUID>
interface FileAssetRepository : JpaRepository<FileAsset, UUID> { fun findAllByEventIdAndModuleCodeAndActiveTrue(eventId:UUID,moduleCode:String):List<FileAsset> }
