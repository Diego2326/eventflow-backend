package com.eventflow.eventflow_api.infrastructure.persistence

import com.eventflow.eventflow_api.application.port.*
import com.eventflow.eventflow_api.domain.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID

interface EventRepository : JpaRepository<EventEntity, UUID>, EventRepositoryPort {
    @Query("select distinct e from EventEntity e left join EventCollaborator c on c.eventId=e.id where e.ownerUserId=:userId or c.userId=:userId")
    override fun findAccessible(userId: UUID): List<EventEntity>
}
interface EventCollaboratorRepository : JpaRepository<EventCollaborator, EventCollaboratorId>, EventCollaboratorRepositoryPort {
    override fun existsByEventIdAndUserId(eventId: UUID, userId: UUID): Boolean
    override fun findByEventIdAndUserId(eventId: UUID, userId: UUID): EventCollaborator?
    override fun findAllByEventId(eventId: UUID): List<EventCollaborator>
}
interface ModuleCatalogRepository : JpaRepository<ModuleCatalog, String>, ModuleCatalogRepositoryPort
interface ModuleDependencyRepository : JpaRepository<ModuleDependency, ModuleDependencyId>, ModuleDependencyRepositoryPort { override fun findAllByModuleCode(moduleCode: String): List<ModuleDependency> }
interface EventModuleRepository : JpaRepository<EventModule, EventModuleId>, EventModuleRepositoryPort { override fun findAllByEventIdOrderByDisplayOrder(eventId: UUID): List<EventModule> }
interface MarketplaceOfferingRepository : JpaRepository<MarketplaceOffering, UUID>, MarketplaceOfferingRepositoryPort {
    override fun findAllByTypeAndStatus(type: OfferingType, status: OfferingStatus): List<MarketplaceOffering>
    @Query("select o from MarketplaceOffering o where o.type=:type and o.status='ACTIVE' and (:category is null or lower(o.category)=lower(:category)) and (:location is null or lower(o.location) like lower(concat('%',:location,'%'))) and (:minCapacity is null or o.capacity>=:minCapacity) and (:maxPrice is null or o.price<=:maxPrice) order by o.createdAt desc")
    fun searchPage(type: OfferingType, category: String?, location: String?, minCapacity: Int?, maxPrice: java.math.BigDecimal?, pageable: org.springframework.data.domain.Pageable): List<MarketplaceOffering>
    override fun search(type: OfferingType, category: String?, location: String?, minCapacity: Int?, maxPrice: java.math.BigDecimal?, limit: Int) = searchPage(type, category, location, minCapacity, maxPrice, org.springframework.data.domain.PageRequest.of(0, limit))
    override fun findAllByOwnerUserId(ownerUserId: UUID): List<MarketplaceOffering>
}
interface OfferingAvailabilityRepository : JpaRepository<OfferingAvailability, UUID>, OfferingAvailabilityRepositoryPort { override fun findAllByOfferingId(offeringId: UUID): List<OfferingAvailability> }
interface ReservationRepository : JpaRepository<Reservation, UUID>, ReservationRepositoryPort {
    override fun findAllByEventId(eventId: UUID): List<Reservation>
    @Query("select count(r)>0 from Reservation r where r.offeringId=:offeringId and r.status='ACCEPTED' and r.startsAt<:endsAt and r.endsAt>:startsAt")
    override fun hasConflict(offeringId: UUID, startsAt: Instant, endsAt: Instant): Boolean
}
interface SimulatedPaymentRepository : JpaRepository<SimulatedPayment, UUID>, SimulatedPaymentRepositoryPort { override fun findAllByReservationId(reservationId: UUID): List<SimulatedPayment> }
interface InvitationRepository : JpaRepository<Invitation, UUID>, InvitationRepositoryPort {
    override fun findByTokenHash(tokenHash: String): Invitation?
    override fun findAllByEventId(eventId: UUID): List<Invitation>
    override fun findAllByLinkedUserId(linkedUserId: UUID): List<Invitation>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Invitation i where i.id=:id") override fun findLocked(id:UUID):Invitation?
}
interface GuestAccessLogRepository : JpaRepository<GuestAccessLog, UUID>, GuestAccessLogRepositoryPort {
    override fun findAllByInvitationIdOrderByCreatedAt(invitationId: UUID): List<GuestAccessLog>
    override fun findAllByInvitationIdIn(invitationIds: Collection<UUID>): List<GuestAccessLog>
}
interface AgendaItemRepository : JpaRepository<AgendaItem, UUID>, AgendaItemRepositoryPort {
    override fun findAllByEventIdOrderByStartsAt(eventId: UUID): List<AgendaItem>
    override fun findAllByStartsAtBetweenAndStatusNot(from: Instant, to: Instant, status: String): List<AgendaItem>
}
interface AgendaFavoriteRepository : JpaRepository<AgendaFavorite, AgendaFavoriteId>, AgendaFavoriteRepositoryPort {
    override fun findAllByUserId(userId: UUID): List<AgendaFavorite>
    override fun findAllByAgendaItemIdIn(agendaItemIds: Collection<UUID>): List<AgendaFavorite>
}
interface AssistanceRequestRepository : JpaRepository<AssistanceRequest, UUID>, AssistanceRequestRepositoryPort {
    override fun findAllByEventIdOrderByPriorityDescCreatedAtAsc(eventId: UUID): List<AssistanceRequest>
    override fun findAllByInvitationIdOrderByCreatedAtDesc(invitationId: UUID): List<AssistanceRequest>
}
interface NotificationRepository : JpaRepository<NotificationEntity, UUID>, NotificationRepositoryPort {
    override fun findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId: UUID): List<NotificationEntity>
    override fun existsByDedupeKey(dedupeKey: String): Boolean
}
interface ConversationMessageRepository : JpaRepository<ConversationMessage, UUID>, ConversationMessageRepositoryPort {
    override fun findAllByEventIdAndChannelOrderByCreatedAt(eventId: UUID, channel: String): List<ConversationMessage>
    @Query("select m from ConversationMessage m where m.eventId=:eventId and m.channel=:channel and " +
        "(m.senderUserId=:userId or m.recipientUserId=:userId or exists " +
        "(select r.id from Reservation r, MarketplaceOffering o where r.id=m.reservationId " +
        "and o.id=r.offeringId and (r.requesterUserId=:userId or o.ownerUserId=:userId))) " +
        "order by m.createdAt")
    override fun findVisible(eventId: UUID, channel: String, userId: UUID): List<ConversationMessage>
}
interface ModuleRecordRepository : JpaRepository<ModuleRecord, UUID>, ModuleRecordRepositoryPort {
    override fun findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId: UUID, moduleCode: String, recordType: String): List<ModuleRecord>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ModuleRecord r where r.id=:id") override fun findLocked(id: UUID): ModuleRecord?
}
interface ModuleActionRepository : JpaRepository<ModuleAction, UUID>, ModuleActionRepositoryPort {
    override fun findAllByModuleRecordIdOrderByCreatedAt(moduleRecordId: UUID): List<ModuleAction>
    override fun findAllByModuleRecordIdAndActorUserIdOrderByCreatedAt(moduleRecordId: UUID, actorUserId: UUID): List<ModuleAction>
    override fun existsByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): Boolean
    override fun findByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): ModuleAction?
}
interface ReviewRepository : JpaRepository<Review, UUID>, ReviewRepositoryPort { override fun existsByReservationIdAndAuthorUserId(reservationId: UUID, authorUserId: UUID): Boolean }
interface ModerationReportRepository : JpaRepository<ModerationReport, UUID>, ModerationReportRepositoryPort
interface RoleRequestRepository : JpaRepository<RoleRequest, UUID>, RoleRequestRepositoryPort
interface AuditLogRepository : JpaRepository<AuditLog, UUID>, AuditLogRepositoryPort
interface FileAssetRepository : JpaRepository<FileAsset, UUID>, FileAssetRepositoryPort { override fun findAllByEventIdAndModuleCodeAndActiveTrue(eventId:UUID,moduleCode:String):List<FileAsset> }
