package com.eventflow.eventflow_api.resource.application

import com.eventflow.eventflow_api.event.application.EventService
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.invitation.application.port.GuestAccessLogRepositoryPort
import com.eventflow.eventflow_api.invitation.application.port.InvitationRepositoryPort
import com.eventflow.eventflow_api.module.application.port.ModuleRecordRepositoryPort
import com.eventflow.eventflow_api.module.domain.ModuleRecord
import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import com.eventflow.eventflow_api.shared.application.error.ConflictException
import com.eventflow.eventflow_api.shared.application.error.NotFoundException
import com.eventflow.eventflow_api.shared.application.port.JsonCodec
import com.eventflow.eventflow_api.storage.application.port.FileAssetRepositoryPort
import com.eventflow.eventflow_api.storage.domain.FileModerationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class CertificateIssueRequest(val recipientUserId:UUID,val fileId:UUID,val title:String)
data class CertificateResponse(val id:UUID,val recipientUserId:UUID,val fileId:UUID,val title:String,val downloadUrl:String)

@Service class CertificateService(private val events:EventService,private val invitations:InvitationRepositoryPort,
    private val logs:GuestAccessLogRepositoryPort,private val files:FileAssetRepositoryPort,
    private val records:ModuleRecordRepositoryPort,private val json:JsonCodec){
    @Transactional fun issue(userId:UUID,eventId:UUID,r:CertificateIssueRequest):CertificateResponse{
        if(events.owned(userId,eventId).status!=EventStatus.FINISHED)throw ConflictException("Los certificados se publican después del evento")
        events.requireModule(eventId,"RSC")
        if(r.title.isBlank()||r.title.length>150)throw BadRequestException("Título de certificado inválido")
        val eligible=invitations.findAllByLinkedUserId(r.recipientUserId).any{inv->
            inv.eventId==eventId&&inv.revokedAt==null&&logs.findAllByInvitationIdOrderByCreatedAt(requireNotNull(inv.id))
                .sumOf{if(it.action=="CHECK_IN")it.quantity else -it.quantity}>0
        }
        if(!eligible)throw ConflictException("El invitado no cumple la condición de asistencia")
        val file=files.findById(r.fileId).orElseThrow{NotFoundException("Archivo no encontrado")}
        if(file.eventId!=eventId||file.moduleCode!="RSC"||file.recipientUserId!=r.recipientUserId||
            file.contentType!="application/pdf"||!file.active||file.moderationStatus!=FileModerationStatus.ACTIVE)
            throw BadRequestException("Archivo de certificado inválido")
        if(records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"RSC","CERTIFICATE")
                .any{it.status!="ARCHIVED"&&json.readMap(it.payload)["recipientUserId"]?.toString()==r.recipientUserId.toString()&&
                    json.readMap(it.payload)["fileId"]?.toString()==r.fileId.toString()})throw ConflictException("El certificado ya fue publicado")
        val saved=records.save(ModuleRecord(eventId=eventId,moduleCode="RSC",recordType="CERTIFICATE",ownerUserId=userId,
            title=r.title,payload=json.write(mapOf("recipientUserId" to r.recipientUserId.toString(),"fileId" to r.fileId.toString()))))
        return response(saved)
    }
    @Transactional(readOnly=true) fun mine(userId:UUID,eventId:UUID):List<CertificateResponse>{
        events.requireVisibleModule(userId,eventId,"RSC")
        return records.findAllByEventIdAndModuleCodeAndRecordTypeOrderByCreatedAtDesc(eventId,"RSC","CERTIFICATE")
            .filter{it.status=="ACTIVE"&&json.readMap(it.payload)["recipientUserId"]?.toString()==userId.toString()}.map(::response)
    }
    private fun response(record:ModuleRecord):CertificateResponse{
        val data=json.readMap(record.payload)
        val fileId=UUID.fromString(data["fileId"].toString())
        return CertificateResponse(requireNotNull(record.id),UUID.fromString(data["recipientUserId"].toString()),fileId,
            record.title?:"Certificado","/api/events/${record.eventId}/files/$fileId")
    }
}
