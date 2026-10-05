package com.eventflow.eventflow_api.infrastructure.web.storage

import com.eventflow.eventflow_api.infrastructure.web.common.*
import com.eventflow.eventflow_api.application.storage.*
import com.eventflow.eventflow_api.common.BadRequestException
import com.eventflow.eventflow_api.domain.*
import org.springframework.http.*
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController @RequestMapping("/api/events/{eventId}/files") class StorageController(private val s:StorageService){
    @PostMapping(consumes=[MediaType.MULTIPART_FORM_DATA_VALUE]) @ResponseStatus(HttpStatus.CREATED)
    fun upload(a:Authentication,@PathVariable eventId:UUID,@RequestParam module:String,@RequestPart file:MultipartFile,
               @RequestParam(required=false) recipientUserId:UUID?):FileResponse {
        if (file.isEmpty || file.size > 10 * 1024 * 1024)
            throw BadRequestException("El archivo está vacío o supera 10 MB")
        return s.upload(a.userId(),eventId,module,file.originalFilename,file.contentType,file.bytes,recipientUserId)
    }
    @GetMapping fun list(a:Authentication,@PathVariable eventId:UUID,@RequestParam module:String)=s.list(a.userId(),eventId,module)
    @GetMapping("/{id}") fun download(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID):ResponseEntity<ByteArray>{val file=s.download(a.userId(),eventId,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType)).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"${file.fileName}\"").body(file.bytes)}
    @PatchMapping("/{id}/moderation") fun moderate(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:ModerationRequest)=s.moderate(a.userId(),eventId,id,r.status)
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun remove(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.remove(a.userId(),eventId,id)
}

data class ModerationRequest(val status:FileModerationStatus)
