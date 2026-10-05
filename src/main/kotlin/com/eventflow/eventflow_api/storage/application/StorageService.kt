package com.eventflow.eventflow_api.storage.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.ForbiddenException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.storage.application.port.FileAssetRepositoryPort
import com.eventflow.eventflow_api.storage.domain.FileAsset
import com.eventflow.eventflow_api.storage.domain.FileModerationStatus

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class FileResponse(
    val id: UUID, val eventId: UUID, val moduleCode: String, val originalName: String,
    val contentType: String, val sizeBytes: Long, val downloadUrl: String,
    val moderationStatus: FileModerationStatus, val recipientUserId: UUID?, val official:Boolean
)

@Service
class StorageService(
    private val files: FileAssetRepositoryPort,
    private val eventService: EventService,
    private val objects: ObjectStorage
) {
    private val allowed = setOf("image/jpeg", "image/png", "image/webp", "application/pdf",
        "text/plain", "application/vnd.openxmlformats-officedocument.presentationml.presentation")

    @Transactional
    fun upload(userId: UUID, eventId: UUID, moduleRaw: String, originalName: String?,
               contentType: String?, bytes: ByteArray, recipientUserId: UUID? = null, official:Boolean=false): FileResponse {
        val module = moduleRaw.uppercase()
        eventService.requireVisibleModule(userId, eventId, module)
        val manager = isManager(userId, eventId)
        if (!manager && (module != "GAL" || !eventService.guestUploadsAllowed(eventId, module)))
            throw ForbiddenException("No puedes subir archivos a este módulo")
        if (!manager && recipientUserId != null) throw ForbiddenException("No puedes asignar destinatarios")
        if(official&&(module!="GAL"||!manager||eventService.accessible(userId,eventId).status!=EventStatus.FINISHED))
            throw ForbiddenException("La galería oficial se publica después del evento")
        recipientUserId?.let { eventService.accessible(it, eventId) }
        configured()
        if (bytes.isEmpty() || bytes.size > 10 * 1024 * 1024)
            throw BadRequestException("El archivo está vacío o supera 10 MB")
        val type = contentType ?: "application/octet-stream"
        if (type !in allowed) throw BadRequestException("Tipo de archivo no permitido")
        if (module == "GAL" && !type.startsWith("image/")) throw BadRequestException("La galería solo admite imágenes")
        val safe = (originalName ?: "file").replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        val path = "$eventId/${UUID.randomUUID()}-$safe"
        objects.put(path, type, bytes)
        val status = if (module == "GAL" && !manager && eventService.moderationRequired(eventId, module))
            FileModerationStatus.PENDING else FileModerationStatus.ACTIVE
        val saved = files.save(FileAsset(eventId = eventId, uploaderUserId = userId,
            moduleCode = module, objectPath = path, originalName = safe,
            contentType = type, sizeBytes = bytes.size.toLong(),
            recipientUserId = recipientUserId,moderationStatus = status,official=official))
        return response(saved)
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID, eventId: UUID, module: String): List<FileResponse> {
        eventService.requireVisibleModule(userId, eventId, module.uppercase())
        val manager = isManager(userId, eventId)
        return files.findAllByEventIdAndModuleCodeAndActiveTrue(eventId, module.uppercase())
            .filter { canAccess(it, userId, manager) }.map(::response)
    }

    @Transactional(readOnly = true)
    fun download(userId: UUID, eventId: UUID, id: UUID): StoredFile {
        eventService.accessible(userId, eventId)
        configured()
        val file = find(eventId, id)
        eventService.requireVisibleModule(userId, eventId, file.moduleCode)
        if (!canAccess(file, userId, isManager(userId, eventId))) throw NotFoundException("Archivo no encontrado")
        return StoredFile(objects.get(file.objectPath), file.contentType, file.originalName)
    }

    @Transactional
    fun remove(userId: UUID, eventId: UUID, id: UUID) {
        eventService.accessible(userId, eventId)
        val file = find(eventId, id)
        if (!isManager(userId, eventId) && file.uploaderUserId != userId)
            throw ForbiddenException("No puedes retirar este archivo")
        if (objects.configured) objects.delete(file.objectPath)
        file.active = false
    }

    @Transactional
    fun moderate(userId: UUID, eventId: UUID, id: UUID, status: FileModerationStatus): FileResponse {
        eventService.authorized(userId, eventId, "FILES")
        if (status == FileModerationStatus.PENDING) throw BadRequestException("Decisión inválida")
        val file = find(eventId, id)
        if (file.moduleCode != "GAL" || file.moderationStatus != FileModerationStatus.PENDING)
            throw ConflictException("El archivo no está pendiente de moderación")
        file.moderationStatus = status
        return response(file)
    }

    private fun configured() {
        if (!objects.configured) throw BadRequestException("El almacenamiento de objetos no está configurado")
    }

    private fun find(eventId: UUID, id: UUID) = files.findById(id)
        .orElseThrow { NotFoundException("Archivo no encontrado") }
        .also { if (it.eventId != eventId || !it.active) throw NotFoundException("Archivo no encontrado") }

    private fun isManager(userId: UUID, eventId: UUID) = try {
        eventService.authorized(userId, eventId, "FILES")
        true
    } catch (_: ForbiddenException) { false }

    private fun canAccess(file: FileAsset, userId: UUID, manager: Boolean): Boolean {
        if (manager) return true
        if (file.uploaderUserId == userId) return true
        if (file.moderationStatus != FileModerationStatus.ACTIVE) return false
        if (file.recipientUserId != null && file.recipientUserId != userId) return false
        return file.moduleCode in setOf("GAL", "RSC", "SES", "EXH")
    }

    private fun response(file: FileAsset) = FileResponse(requireNotNull(file.id), file.eventId,
        file.moduleCode, file.originalName, file.contentType, file.sizeBytes,
        "/api/events/${file.eventId}/files/${file.id}", file.moderationStatus,
        file.recipientUserId,file.official)
}
