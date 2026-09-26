package com.eventflow.eventflow_api.storage

import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.event.EventService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

data class FileResponse(val id:UUID,val eventId:UUID,val moduleCode:String,val originalName:String,val contentType:String,val sizeBytes:Long,val downloadUrl:String)

@Service class StorageService(private val files:FileAssetRepository,private val eventService:EventService,@Value("\${app.storage.url:}") private val url:String,@Value("\${app.storage.service-key:}") private val key:String,@Value("\${app.storage.bucket:eventflow}") private val bucket:String){
    private val allowed=setOf("image/jpeg","image/png","image/webp","application/pdf","text/plain","application/vnd.openxmlformats-officedocument.presentationml.presentation")
    @Transactional fun upload(userId:UUID,eventId:UUID,moduleRaw:String,file:MultipartFile):FileResponse{val module=moduleRaw.uppercase();eventService.accessible(userId,eventId);eventService.requireModule(eventId,module);configured();if(file.isEmpty||file.size>10*1024*1024)throw BadRequestException("El archivo está vacío o supera 10 MB");val type=file.contentType?:"application/octet-stream";if(type !in allowed)throw BadRequestException("Tipo de archivo no permitido");val safe=(file.originalFilename?:"file").replace(Regex("[^A-Za-z0-9._-]"),"_").take(120);val path="$eventId/${UUID.randomUUID()}-$safe";client().post().uri("/storage/v1/object/{bucket}/{path}",bucket,path).contentType(MediaType.parseMediaType(type)).header("x-upsert","false").body(file.bytes).retrieve().toBodilessEntity();val saved=files.save(FileAsset(eventId=eventId,uploaderUserId=userId,moduleCode=module,objectPath=path,originalName=safe,contentType=type,sizeBytes=file.size));return response(saved)}
    @Transactional(readOnly=true) fun list(userId:UUID,eventId:UUID,module:String):List<FileResponse>{eventService.owned(userId,eventId);return files.findAllByEventIdAndModuleCodeAndActiveTrue(eventId,module.uppercase()).map(::response)}
    @Transactional(readOnly=true) fun download(userId:UUID,eventId:UUID,id:UUID):ResponseEntity<ByteArray>{eventService.accessible(userId,eventId);configured();val f=find(eventId,id);val body=client().get().uri("/storage/v1/object/{bucket}/{path}",bucket,f.objectPath).retrieve().body(ByteArray::class.java)?:ByteArray(0);return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType)).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"${f.originalName}\"").body(body)}
    @Transactional fun remove(userId:UUID,eventId:UUID,id:UUID){eventService.owned(userId,eventId);val f=find(eventId,id);if(key.isNotBlank())client().delete().uri("/storage/v1/object/{bucket}/{path}",bucket,f.objectPath).retrieve().toBodilessEntity();f.active=false}
    private fun client()=RestClient.builder().baseUrl(url.trimEnd('/')).defaultHeader("Authorization","Bearer $key").defaultHeader("apikey",key).build()
    private fun configured(){if(url.isBlank()||key.isBlank())throw BadRequestException("El almacenamiento de objetos no está configurado")}
    private fun find(eventId:UUID,id:UUID)=files.findById(id).orElseThrow{NotFoundException("Archivo no encontrado")}.also{if(it.eventId!=eventId||!it.active)throw NotFoundException("Archivo no encontrado")}
    private fun response(f:FileAsset)=FileResponse(requireNotNull(f.id),f.eventId,f.moduleCode,f.originalName,f.contentType,f.sizeBytes,"/api/events/${f.eventId}/files/${f.id}")
}

@RestController @RequestMapping("/api/events/{eventId}/files") class StorageController(private val s:StorageService){
    @PostMapping(consumes=[MediaType.MULTIPART_FORM_DATA_VALUE]) @ResponseStatus(HttpStatus.CREATED) fun upload(a:Authentication,@PathVariable eventId:UUID,@RequestParam module:String,@RequestPart file:MultipartFile)=s.upload(a.userId(),eventId,module,file)
    @GetMapping fun list(a:Authentication,@PathVariable eventId:UUID,@RequestParam module:String)=s.list(a.userId(),eventId,module)
    @GetMapping("/{id}") fun download(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.download(a.userId(),eventId,id)
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun remove(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=s.remove(a.userId(),eventId,id)
}
