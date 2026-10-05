package com.eventflow.eventflow_api.application.port

import com.eventflow.eventflow_api.domain.*
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** Contrato de persistencia usado por los casos de uso. */
interface CrudPort<T : Any, ID : Any> {
    fun <S : T> save(entity: S): S
    fun findById(id: ID): Optional<T>
    fun findAll(): List<T>
    fun findAllById(ids: Iterable<ID>): List<T>
    fun deleteById(id: ID)
    fun existsById(id: ID): Boolean
}

interface EventRepositoryPort : CrudPort<EventEntity, UUID> {
    fun findAccessible(userId: UUID): List<EventEntity>
}
interface EventCollaboratorRepositoryPort : CrudPort<EventCollaborator, EventCollaboratorId> {
    fun existsByEventIdAndUserId(eventId: UUID, userId: UUID): Boolean
    fun findByEventIdAndUserId(eventId: UUID, userId: UUID): EventCollaborator?
    fun findAllByEventId(eventId: UUID): List<EventCollaborator>
}
interface ModuleCatalogRepositoryPort : CrudPort<ModuleCatalog, String>
interface ModuleDependencyRepositoryPort : CrudPort<ModuleDependency, ModuleDependencyId> {
    fun findAllByModuleCode(moduleCode: String): List<ModuleDependency>
}
interface EventModuleRepositoryPort : CrudPort<EventModule, EventModuleId> {
    fun findAllByEventIdOrderByDisplayOrder(eventId: UUID): List<EventModule>
}
interface MarketplaceOfferingRepositoryPort : CrudPort<MarketplaceOffering, UUID> {
    fun findAllByTypeAndStatus(type: OfferingType, status: OfferingStatus): List<MarketplaceOffering>
    fun search(type: OfferingType, category: String?, location: String?, minCapacity: Int?, maxPrice: java.math.BigDecimal?, limit: Int): List<MarketplaceOffering>
    fun findAllByOwnerUserId(ownerUserId: UUID): List<MarketplaceOffering>
}
interface OfferingAvailabilityRepositoryPort : CrudPort<OfferingAvailability, UUID> {
    fun findAllByOfferingId(offeringId: UUID): List<OfferingAvailability>
}
interface ReservationRepositoryPort : CrudPort<Reservation, UUID> {
    fun findAllByEventId(eventId: UUID): List<Reservation>
    fun hasConflict(offeringId: UUID, startsAt: Instant, endsAt: Instant): Boolean
}
interface SimulatedPaymentRepositoryPort : CrudPort<SimulatedPayment, UUID> {
    fun findAllByReservationId(reservationId: UUID): List<SimulatedPayment>
}
interface InvitationRepositoryPort : CrudPort<Invitation, UUID> {
    fun findByTokenHash(tokenHash: String): Invitation?
    fun findAllByEventId(eventId: UUID): List<Invitation>
    fun findAllByLinkedUserId(linkedUserId: UUID): List<Invitation>
    fun findLocked(id: UUID): Invitation?
}
interface GuestAccessLogRepositoryPort : CrudPort<GuestAccessLog, UUID> {
    fun findAllByInvitationIdOrderByCreatedAt(invitationId: UUID): List<GuestAccessLog>
    fun findAllByInvitationIdIn(invitationIds: Collection<UUID>): List<GuestAccessLog>
}
interface AgendaItemRepositoryPort : CrudPort<AgendaItem, UUID> {
    fun findAllByEventIdOrderByStartsAt(eventId: UUID): List<AgendaItem>
    fun findAllByStartsAtBetweenAndStatusNot(from: Instant, to: Instant, status: String): List<AgendaItem>
}
interface AgendaFavoriteRepositoryPort : CrudPort<AgendaFavorite, AgendaFavoriteId> {
    fun findAllByUserId(userId: UUID): List<AgendaFavorite>
    fun findAllByAgendaItemIdIn(agendaItemIds: Collection<UUID>): List<AgendaFavorite>
}
interface AssistanceRequestRepositoryPort : CrudPort<AssistanceRequest, UUID> {
    fun findAllByEventIdOrderByPriorityDescCreatedAtAsc(eventId: UUID): List<AssistanceRequest>
    fun findAllByInvitationIdOrderByCreatedAtDesc(invitationId: UUID): List<AssistanceRequest>
}
interface NotificationRepositoryPort : CrudPort<NotificationEntity, UUID> {
    fun findAllByEventIdAndActiveTrueOrderByCreatedAtDesc(eventId: UUID): List<NotificationEntity>
    fun existsByDedupeKey(dedupeKey: String): Boolean
}
interface ConversationMessageRepositoryPort : CrudPort<ConversationMessage, UUID> {
    fun findAllByEventIdAndChannelOrderByCreatedAt(eventId: UUID, channel: String): List<ConversationMessage>
    fun findVisible(eventId: UUID, channel: String, userId: UUID): List<ConversationMessage>
}
interface ModuleRecordRepositoryPort : CrudPort<ModuleRecord, UUID> {
    fun findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId: UUID, moduleCode: String, recordType: String): List<ModuleRecord>
    fun findLocked(id: UUID): ModuleRecord?
}
interface ModuleActionRepositoryPort : CrudPort<ModuleAction, UUID> {
    fun findAllByModuleRecordIdOrderByCreatedAt(moduleRecordId: UUID): List<ModuleAction>
    fun findAllByModuleRecordIdAndActorUserIdOrderByCreatedAt(moduleRecordId: UUID, actorUserId: UUID): List<ModuleAction>
    fun existsByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): Boolean
    fun findByModuleRecordIdAndActorUserIdAndActionType(moduleRecordId: UUID, actorUserId: UUID, actionType: String): ModuleAction?
}
interface ReviewRepositoryPort : CrudPort<Review, UUID> {
    fun existsByReservationIdAndAuthorUserId(reservationId: UUID, authorUserId: UUID): Boolean
}
interface ModerationReportRepositoryPort : CrudPort<ModerationReport, UUID>
interface RoleRequestRepositoryPort : CrudPort<RoleRequest, UUID>
interface AuditLogRepositoryPort : CrudPort<AuditLog, UUID>
interface FileAssetRepositoryPort : CrudPort<FileAsset, UUID> {
    fun findAllByEventIdAndModuleCodeAndActiveTrue(eventId: UUID, moduleCode: String): List<FileAsset>
}
