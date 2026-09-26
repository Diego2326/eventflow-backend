package com.eventflow.eventflow_api.domain

import jakarta.persistence.*
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class EventStatus { DRAFT, PUBLISHED, RUNNING, FINISHED, CANCELLED }
enum class OfferingType { SPACE, SERVICE }
enum class OfferingStatus { DRAFT, ACTIVE, INACTIVE }
enum class ReservationStatus { PENDING, ACCEPTED, REJECTED, CANCELLED, COMPLETED }
enum class InvitationStatus { PENDING, ACCEPTED, DECLINED }
enum class AssistanceStatus { RECEIVED, ACCEPTED, ON_THE_WAY, ATTENDED, CANCELLED }

@Entity @Table(name = "event")
class EventEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "event_id") var id: UUID? = null,
    @Column(name = "owner_user_id", nullable = false) var ownerUserId: UUID,
    @Column(name = "event_name", nullable = false) var name: String,
    @Column(name = "event_type", nullable = false) var type: String,
    @Column(columnDefinition = "text") var description: String? = null,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "ends_at") var endsAt: Instant? = null,
    @Column(nullable = false) var timezone: String = "America/Guatemala",
    var location: String? = null,
    @Column(name = "estimated_capacity") var estimatedCapacity: Int? = null,
    var budget: BigDecimal? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: EventStatus = EventStatus.DRAFT,
    @Column(name = "reentry_allowed", nullable = false) var reentryAllowed: Boolean = false,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)

@Entity @Table(name = "event_collaborator") @IdClass(EventCollaboratorId::class)
class EventCollaborator(
    @Id @Column(name = "event_id") var eventId: UUID = UUID.randomUUID(),
    @Id @Column(name = "user_id") var userId: UUID = UUID.randomUUID(),
    @Column(nullable = false, columnDefinition = "text") var permissions: String = ""
)
data class EventCollaboratorId(var eventId: UUID? = null, var userId: UUID? = null) : Serializable

@Entity @Table(name = "module_catalog")
class ModuleCatalog(
    @Id @Column(name = "module_code") var code: String = "",
    @Column(name = "module_name", nullable = false) var name: String = "",
    @Column(nullable = false) var category: String = "",
    @Column(nullable = false, columnDefinition = "text") var description: String = "",
    @Column(name = "globally_enabled", nullable = false) var globallyEnabled: Boolean = true
)

@Entity @Table(name = "module_dependency") @IdClass(ModuleDependencyId::class)
class ModuleDependency(
    @Id @Column(name = "module_code") var moduleCode: String = "",
    @Id @Column(name = "required_module_code") var requiredModuleCode: String = ""
)
data class ModuleDependencyId(var moduleCode: String? = null, var requiredModuleCode: String? = null) : Serializable

@Entity @Table(name = "event_module") @IdClass(EventModuleId::class)
class EventModule(
    @Id @Column(name = "event_id") var eventId: UUID = UUID.randomUUID(),
    @Id @Column(name = "module_code") var moduleCode: String = "",
    @Column(nullable = false) var enabled: Boolean = true,
    @Column(name = "display_order", nullable = false) var displayOrder: Int = 0,
    @Column(nullable = false) var featured: Boolean = false,
    @Column(nullable = false, columnDefinition = "text") var configuration: String = "{}",
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)
data class EventModuleId(var eventId: UUID? = null, var moduleCode: String? = null) : Serializable

@Entity @Table(name = "marketplace_offering")
class MarketplaceOffering(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "offering_id") var id: UUID? = null,
    @Column(name = "owner_user_id", nullable = false) var ownerUserId: UUID,
    @Enumerated(EnumType.STRING) @Column(name = "offering_type", nullable = false) var type: OfferingType,
    @Column(nullable = false) var name: String,
    var category: String? = null,
    @Column(columnDefinition = "text") var description: String? = null,
    var location: String? = null,
    var capacity: Int? = null,
    @Column(nullable = false) var price: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, columnDefinition = "text") var attributes: String = "{}",
    @Column(name = "image_urls", nullable = false, columnDefinition = "text") var imageUrls: String = "[]",
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: OfferingStatus = OfferingStatus.ACTIVE,
    @Column(nullable = false) var rating: BigDecimal = BigDecimal.ZERO,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "offering_availability")
class OfferingAvailability(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "availability_id") var id: UUID? = null,
    @Column(name = "offering_id", nullable = false) var offeringId: UUID,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "ends_at", nullable = false) var endsAt: Instant,
    @Column(nullable = false) var available: Boolean = true
)

@Entity @Table(name = "reservation")
class Reservation(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "reservation_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "offering_id", nullable = false) var offeringId: UUID,
    @Column(name = "requester_user_id", nullable = false) var requesterUserId: UUID,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "ends_at", nullable = false) var endsAt: Instant,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: ReservationStatus = ReservationStatus.PENDING,
    @Column(columnDefinition = "text") var note: String? = null,
    @Column(name = "decided_at") var decidedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "simulated_payment")
class SimulatedPayment(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "payment_id") var id: UUID? = null,
    @Column(name = "reservation_id", nullable = false) var reservationId: UUID,
    @Column(nullable = false) var amount: BigDecimal,
    @Column(nullable = false) var status: String,
    @Column(name = "paid_at", nullable = false) var paidAt: Instant = Instant.now(),
    @Column(nullable = false, unique = true) var reference: String
)

@Entity @Table(name = "invitation")
class Invitation(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "invitation_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "linked_user_id") var linkedUserId: UUID? = null,
    @Column(name = "guest_name", nullable = false) var guestName: String,
    @Column(name = "guest_email") var guestEmail: String? = null,
    @Column(name = "token_hash", nullable = false, unique = true) var tokenHash: String,
    @Column(name = "token_expires_at") var tokenExpiresAt: Instant? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: InvitationStatus = InvitationStatus.PENDING,
    @Column(name = "allowed_capacity", nullable = false) var allowedCapacity: Int = 1,
    @Column(nullable = false, columnDefinition = "text") var companions: String = "[]",
    @Column(name = "table_label") var tableLabel: String? = null,
    @Column(name = "seat_label") var seatLabel: String? = null,
    @Column(name = "sector_label") var sectorLabel: String? = null,
    @Column(name = "revoked_at") var revokedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "guest_access_log")
class GuestAccessLog(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "access_log_id") var id: UUID? = null,
    @Column(name = "invitation_id", nullable = false) var invitationId: UUID,
    @Column(nullable = false) var action: String,
    @Column(nullable = false) var quantity: Int = 1,
    @Column(name = "performed_by") var performedBy: UUID? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "agenda_item")
class AgendaItem(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "agenda_item_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(nullable = false) var title: String,
    @Column(columnDefinition = "text") var description: String? = null,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "ends_at", nullable = false) var endsAt: Instant,
    var zone: String? = null,
    var responsible: String? = null,
    @Column(nullable = false) var status: String = "SCHEDULED",
    var capacity: Int? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "agenda_favorite") @IdClass(AgendaFavoriteId::class)
class AgendaFavorite(
    @Id @Column(name = "agenda_item_id") var agendaItemId: UUID = UUID.randomUUID(),
    @Id @Column(name = "user_id") var userId: UUID = UUID.randomUUID()
)
data class AgendaFavoriteId(var agendaItemId: UUID? = null, var userId: UUID? = null) : Serializable

@Entity @Table(name = "assistance_request")
class AssistanceRequest(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "assistance_request_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "invitation_id") var invitationId: UUID? = null,
    @Column(name = "requester_user_id") var requesterUserId: UUID? = null,
    @Column(name = "assigned_user_id") var assignedUserId: UUID? = null,
    @Column(nullable = false) var category: String,
    @Column(columnDefinition = "text") var details: String? = null,
    var location: String? = null,
    @Column(nullable = false) var priority: Int = 0,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: AssistanceStatus = AssistanceStatus.RECEIVED,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)

@Entity @Table(name = "notification")
class NotificationEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "notification_id") var id: UUID? = null,
    @Column(name = "event_id") var eventId: UUID? = null,
    @Column(name = "author_user_id") var authorUserId: UUID? = null,
    @Column(name = "recipient_user_id") var recipientUserId: UUID? = null,
    @Column(nullable = false) var title: String,
    @Column(nullable = false, columnDefinition = "text") var body: String,
    @Column(name = "audience_type", nullable = false) var audienceType: String = "ALL",
    @Column(name = "audience_value") var audienceValue: String? = null,
    @Column(nullable = false) var channel: String = "IN_APP",
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "conversation_message")
class ConversationMessage(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "message_id") var id: UUID? = null,
    @Column(name = "event_id") var eventId: UUID? = null,
    @Column(name = "reservation_id") var reservationId: UUID? = null,
    @Column(name = "sender_user_id", nullable = false) var senderUserId: UUID,
    @Column(name = "recipient_user_id") var recipientUserId: UUID? = null,
    @Column(nullable = false) var channel: String,
    @Column(nullable = false, columnDefinition = "text") var body: String,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "module_record")
class ModuleRecord(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "module_record_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "module_code", nullable = false) var moduleCode: String,
    @Column(name = "record_type", nullable = false) var recordType: String,
    @Column(name = "owner_user_id") var ownerUserId: UUID? = null,
    @Column(name = "invitation_id") var invitationId: UUID? = null,
    @Column(name = "parent_record_id") var parentRecordId: UUID? = null,
    @Column(nullable = false) var status: String = "ACTIVE",
    var title: String? = null,
    @Column(nullable = false, columnDefinition = "text") var payload: String = "{}",
    var capacity: Int? = null,
    @Column(name = "current_count", nullable = false) var currentCount: Int = 0,
    @Column(name = "starts_at") var startsAt: Instant? = null,
    @Column(name = "ends_at") var endsAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Version var version: Long = 0
)

@Entity @Table(name = "module_action")
class ModuleAction(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "module_action_id") var id: UUID? = null,
    @Column(name = "module_record_id", nullable = false) var moduleRecordId: UUID,
    @Column(name = "actor_user_id") var actorUserId: UUID? = null,
    @Column(name = "invitation_id") var invitationId: UUID? = null,
    @Column(name = "action_type", nullable = false) var actionType: String,
    @Column(nullable = false, columnDefinition = "text") var payload: String = "{}",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "review")
class Review(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "review_id") var id: UUID? = null,
    @Column(name = "reservation_id", nullable = false) var reservationId: UUID,
    @Column(name = "author_user_id", nullable = false) var authorUserId: UUID,
    @Column(nullable = false) var rating: Int,
    @Column(columnDefinition = "text") var comment: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "moderation_report")
class ModerationReport(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "report_id") var id: UUID? = null,
    @Column(name = "reporter_user_id") var reporterUserId: UUID? = null,
    @Column(name = "event_id") var eventId: UUID? = null,
    @Column(name = "target_type", nullable = false) var targetType: String,
    @Column(name = "target_id", nullable = false) var targetId: UUID,
    @Column(nullable = false, columnDefinition = "text") var reason: String,
    @Column(nullable = false) var status: String = "PENDING",
    @Column(columnDefinition = "text") var resolution: String? = null,
    @Column(name = "resolved_by") var resolvedBy: UUID? = null,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "role_request")
class RoleRequest(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "role_request_id") var id: UUID? = null,
    @Column(name = "user_id", nullable = false) var userId: UUID,
    @Column(name = "requested_role", nullable = false) var requestedRole: String,
    @Column(nullable = false) var status: String = "PENDING",
    @Column(name = "decided_by") var decidedBy: UUID? = null,
    @Column(name = "decided_at") var decidedAt: Instant? = null,
    @Column(columnDefinition = "text") var reason: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "audit_log")
class AuditLog(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "audit_log_id") var id: UUID? = null,
    @Column(name = "actor_user_id") var actorUserId: UUID? = null,
    @Column(name = "event_id") var eventId: UUID? = null,
    @Column(nullable = false) var action: String,
    @Column(name = "target_type") var targetType: String? = null,
    @Column(name = "target_id") var targetId: UUID? = null,
    @Column(nullable = false, columnDefinition = "text") var details: String = "{}",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)

@Entity @Table(name = "file_asset")
class FileAsset(
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "file_asset_id") var id: UUID? = null,
    @Column(name = "event_id", nullable = false) var eventId: UUID,
    @Column(name = "uploader_user_id", nullable = false) var uploaderUserId: UUID,
    @Column(name = "module_code", nullable = false) var moduleCode: String,
    @Column(name = "object_path", nullable = false, unique = true, columnDefinition = "text") var objectPath: String,
    @Column(name = "original_name", nullable = false) var originalName: String,
    @Column(name = "content_type", nullable = false) var contentType: String,
    @Column(name = "size_bytes", nullable = false) var sizeBytes: Long,
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
