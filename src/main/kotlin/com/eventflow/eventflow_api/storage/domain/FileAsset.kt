package com.eventflow.eventflow_api.storage.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

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
    @Column(name = "recipient_user_id") var recipientUserId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(name = "moderation_status", nullable = false) var moderationStatus: FileModerationStatus = FileModerationStatus.ACTIVE,
    @Column(nullable = false) var active: Boolean = true,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
