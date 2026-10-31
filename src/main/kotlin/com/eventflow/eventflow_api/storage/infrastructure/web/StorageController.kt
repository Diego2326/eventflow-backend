package com.eventflow.eventflow_api.storage.infrastructure.web

import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.infrastructure.web.userId
import com.eventflow.eventflow_api.storage.application.FileResponse
import com.eventflow.eventflow_api.storage.application.DirectUploadRequest
import com.eventflow.eventflow_api.storage.application.StorageService
import com.eventflow.eventflow_api.storage.domain.FileModerationStatus

import org.springframework.http.*
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID
import java.net.URI

@RestController @RequestMapping("/api/events/{eventId}/files") class StorageController(private val s:StorageService){
    @PostMapping(consumes=[MediaType.MULTIPART_FORM_DATA_VALUE]) @ResponseStatus(HttpStatus.CREATED)
    fun upload(a:Authentication,@PathVariable eventId:UUID,@RequestParam module:String,@RequestPart file:MultipartFile,
               @RequestParam(required=false) recipientUserId:UUID?,@RequestParam(defaultValue="false") official:Boolean):FileResponse {
        if (module.equals("GAL", ignoreCase = true))
            throw BadRequestException("Las fotos de galería se suben directamente al almacenamiento del evento")
        if (file.isEmpty || file.size > 10 * 1024 * 1024)
            throw BadRequestException("El archivo está vacío o supera 10 MB")
        return s.upload(a.userId(),eventId,module,file.originalFilename,file.contentType,file.bytes,recipientUserId,official)
    }
    @PostMapping("/upload-url") @ResponseStatus(HttpStatus.CREATED)
    fun uploadUrl(a:Authentication,@PathVariable eventId:UUID,@RequestBody request:DirectUploadRequest) =
        s.createDirectUpload(a.userId(),eventId,request)

    @GetMapping fun list(a:Authentication,@PathVariable eventId:UUID,@RequestParam module:String)=s.list(a.userId(),eventId,module)
    @PostMapping("/{id}/complete-upload")
    fun completeUpload(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID):FileResponse =
        s.completeDirectUpload(a.userId(),eventId,id)

    @DeleteMapping("/{id}/upload") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelUpload(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID) =
        s.cancelDirectUpload(a.userId(),eventId,id)

    @GetMapping("/{id}") fun download(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID):ResponseEntity<ByteArray>{
        val url = s.directDownloadUrl(a.userId(),eventId,id)
        if (url != null) return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).body(ByteArray(0))
        val file=s.download(a.userId(),eventId,id)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType)).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"${file.fileName}\"").body(file.bytes)
    }
    @PatchMapping("/{id}/moderation") fun moderate(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:ModerationRequest)=s.moderate(a.userId(),eventId,id,r.status)
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun remove(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.remove(a.userId(),eventId,id)
}

data class ModerationRequest(val status:FileModerationStatus)
