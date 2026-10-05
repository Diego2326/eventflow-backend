package com.eventflow.eventflow_api.application.marketplace

import com.eventflow.eventflow_api.application.port.*

import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.application.event.EventService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OfferingRequest(val type:OfferingType,val name:String,val category:String?=null,val description:String?=null,val location:String?=null,val capacity:Int?=null,val price:BigDecimal=BigDecimal.ZERO,val attributes:String="{}",val imageUrls:String="[]")
data class AvailabilityRequest(val startsAt:Instant,val endsAt:Instant,val available:Boolean=true)
data class ReservationRequest(val eventId:UUID,val offeringId:UUID,val startsAt:Instant,val endsAt:Instant,val note:String?=null)
data class DecisionRequest(val accepted:Boolean)
data class PaymentRequest(val amount:BigDecimal,val status:String="PAID")
data class ReviewRequest(val rating:Int,val comment:String?=null)

@Service class MarketplaceService(private val offerings:MarketplaceOfferingRepositoryPort,private val availability:OfferingAvailabilityRepositoryPort,private val reservations:ReservationRepositoryPort,private val payments:SimulatedPaymentRepositoryPort,private val reviews:ReviewRepositoryPort,private val events:EventService){
    @Transactional fun create(userId:UUID,r:OfferingRequest):MarketplaceOffering{validateOffering(r);return offerings.save(MarketplaceOffering(ownerUserId=userId,type=r.type,name=r.name.trim(),category=r.category,description=r.description,location=r.location,capacity=r.capacity,price=r.price,attributes=r.attributes,imageUrls=r.imageUrls))}
    @Transactional fun update(userId:UUID,id:UUID,r:OfferingRequest):MarketplaceOffering{validateOffering(r);val o=ownOffering(userId,id);o.type=r.type;o.name=r.name.trim();o.category=r.category;o.description=r.description;o.location=r.location;o.capacity=r.capacity;o.price=r.price;o.attributes=r.attributes;o.imageUrls=r.imageUrls;return o}
    @Transactional fun status(userId:UUID,id:UUID,status:OfferingStatus):MarketplaceOffering{val o=ownOffering(userId,id);o.status=status;return o}
    @Transactional(readOnly=true) fun search(type:OfferingType,category:String?,location:String?,minCapacity:Int?,maxPrice:BigDecimal?,limit:Int=50): List<MarketplaceOffering> {if(limit !in 1..100||minCapacity!=null&&minCapacity<1||maxPrice!=null&&maxPrice<BigDecimal.ZERO)throw BadRequestException("Filtros de búsqueda inválidos");return offerings.search(type,category?.trim()?.ifBlank{null},location?.trim()?.ifBlank{null},minCapacity,maxPrice,limit)}
    @Transactional fun availability(userId:UUID,id:UUID,r:AvailabilityRequest):OfferingAvailability{ownOffering(userId,id);if(!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Período inválido");return availability.save(OfferingAvailability(offeringId=id,startsAt=r.startsAt,endsAt=r.endsAt,available=r.available))}
    @Transactional(readOnly=true) fun availability(id:UUID)=availability.findAllByOfferingId(id)
    @Transactional fun reserve(userId:UUID,r:ReservationRequest):Reservation{events.owned(userId,r.eventId);if(!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Período inválido");val o=offerings.findById(r.offeringId).orElseThrow{NotFoundException("Publicación no encontrada")};if(o.status!=OfferingStatus.ACTIVE)throw ConflictException("La publicación no está activa");if(o.ownerUserId==userId)throw ConflictException("No puedes reservar tu propia publicación");checkAvailability(r.offeringId,r.startsAt,r.endsAt);return reservations.save(Reservation(eventId=r.eventId,offeringId=r.offeringId,requesterUserId=userId,startsAt=r.startsAt,endsAt=r.endsAt,note=r.note))}
    @Transactional(readOnly=true) fun eventReservations(userId:UUID,eventId:UUID):List<Reservation>{events.owned(userId,eventId);return reservations.findAllByEventId(eventId)}
    @Transactional fun decide(userId:UUID,id:UUID,r:DecisionRequest):Reservation{val res=findReservation(id);ownOffering(userId,res.offeringId);if(res.status!=ReservationStatus.PENDING)throw ConflictException("La reservación ya fue decidida");if(r.accepted)checkAvailability(res.offeringId,res.startsAt,res.endsAt);res.status=if(r.accepted)ReservationStatus.ACCEPTED else ReservationStatus.REJECTED;res.decidedAt=Instant.now();return res}
    @Transactional fun cancel(userId:UUID,id:UUID):Reservation{val r=findReservation(id);val o=offerings.findById(r.offeringId).orElseThrow{NotFoundException("Publicación no encontrada")};if(r.requesterUserId!=userId&&o.ownerUserId!=userId)throw ForbiddenException("No puedes cancelar esta reservación");if(r.status in setOf(ReservationStatus.CANCELLED,ReservationStatus.COMPLETED))throw ConflictException("La reservación no se puede cancelar");r.status=ReservationStatus.CANCELLED;return r}
    @Transactional fun payment(userId:UUID,id:UUID,r:PaymentRequest):SimulatedPayment{val res=findReservation(id);if(res.requesterUserId!=userId)throw ForbiddenException("No puedes registrar este pago");if(res.status!=ReservationStatus.ACCEPTED)throw ConflictException("La reservación debe estar aceptada");if(r.amount<=BigDecimal.ZERO||r.status!="PAID")throw BadRequestException("Pago simulado inválido");if(payments.findAllByReservationId(id).any{it.status=="PAID"})throw ConflictException("La reservación ya tiene un pago");return payments.save(SimulatedPayment(reservationId=id,amount=r.amount,status="PAID",reference="SIM-${UUID.randomUUID().toString().uppercase()}"))}
    @Transactional(readOnly=true) fun receipts(userId:UUID,id:UUID):List<SimulatedPayment>{val res=findReservation(id);val owner=offerings.findById(res.offeringId).map{it.ownerUserId}.orElse(null);if(res.requesterUserId!=userId&&owner!=userId)throw ForbiddenException("No puedes consultar estos comprobantes");return payments.findAllByReservationId(id)}
    @Transactional fun complete(userId:UUID,id:UUID):Reservation{val res=findReservation(id);ownOffering(userId,res.offeringId);if(res.status!=ReservationStatus.ACCEPTED)throw ConflictException("Solo una reservación aceptada puede concluir");res.status=ReservationStatus.COMPLETED;return res}
    @Transactional fun review(userId:UUID,id:UUID,r:ReviewRequest):Review{val res=findReservation(id);if(res.requesterUserId!=userId||res.status!=ReservationStatus.COMPLETED)throw ForbiddenException("La contratación no es elegible");if(r.rating !in 1..5)throw BadRequestException("Calificación inválida");if(reviews.existsByReservationIdAndAuthorUserId(id,userId))throw ConflictException("Ya calificaste esta contratación");return reviews.save(Review(reservationId=id,authorUserId=userId,rating=r.rating,comment=r.comment))}
    private fun ownOffering(userId:UUID,id:UUID)=offerings.findById(id).orElseThrow{NotFoundException("Publicación no encontrada")}.also{if(it.ownerUserId!=userId)throw ForbiddenException("No eres propietario")}
    private fun findReservation(id:UUID)=reservations.findById(id).orElseThrow{NotFoundException("Reservación no encontrada")}
    private fun validateOffering(r:OfferingRequest){if(r.name.isBlank()||r.price<BigDecimal.ZERO||r.capacity!=null&&r.capacity<1)throw BadRequestException("Datos de publicación inválidos")}
    private fun checkAvailability(offeringId:UUID,startsAt:Instant,endsAt:Instant){
        if(reservations.hasConflict(offeringId,startsAt,endsAt))throw ConflictException("El período ya está reservado")
        val ranges=availability.findAllByOfferingId(offeringId)
        if(ranges.any{!it.available&&it.startsAt<endsAt&&it.endsAt>startsAt})throw ConflictException("El período no está disponible")
        if(ranges.any{it.available}&&ranges.none{it.available&&!startsAt.isBefore(it.startsAt)&&!endsAt.isAfter(it.endsAt)})throw ConflictException("El período no está disponible")
    }
}
