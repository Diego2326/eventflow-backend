package com.eventflow.eventflow_api.marketplace

import com.eventflow.eventflow_api.common.*
import com.eventflow.eventflow_api.domain.*
import com.eventflow.eventflow_api.event.EventService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OfferingRequest(val type:OfferingType,val name:String,val category:String?=null,val description:String?=null,val location:String?=null,val capacity:Int?=null,val price:BigDecimal=BigDecimal.ZERO,val attributes:String="{}",val imageUrls:String="[]")
data class AvailabilityRequest(val startsAt:Instant,val endsAt:Instant,val available:Boolean=true)
data class ReservationRequest(val eventId:UUID,val offeringId:UUID,val startsAt:Instant,val endsAt:Instant,val note:String?=null)
data class DecisionRequest(val accepted:Boolean)
data class PaymentRequest(val amount:BigDecimal,val status:String="PAID")
data class ReviewRequest(val rating:Int,val comment:String?=null)

@Service class MarketplaceService(private val offerings:MarketplaceOfferingRepository,private val availability:OfferingAvailabilityRepository,private val reservations:ReservationRepository,private val payments:SimulatedPaymentRepository,private val reviews:ReviewRepository,private val events:EventService){
    @Transactional fun create(userId:UUID,r:OfferingRequest):MarketplaceOffering{if(r.name.isBlank()||r.price< BigDecimal.ZERO)throw BadRequestException("Nombre y precio válidos son obligatorios");return offerings.save(MarketplaceOffering(ownerUserId=userId,type=r.type,name=r.name.trim(),category=r.category,description=r.description,location=r.location,capacity=r.capacity,price=r.price,attributes=r.attributes,imageUrls=r.imageUrls))}
    @Transactional fun update(userId:UUID,id:UUID,r:OfferingRequest):MarketplaceOffering{val o=ownOffering(userId,id);o.type=r.type;o.name=r.name;o.category=r.category;o.description=r.description;o.location=r.location;o.capacity=r.capacity;o.price=r.price;o.attributes=r.attributes;o.imageUrls=r.imageUrls;return o}
    @Transactional fun status(userId:UUID,id:UUID,status:OfferingStatus):MarketplaceOffering{val o=ownOffering(userId,id);o.status=status;return o}
    @Transactional(readOnly=true) fun search(type:OfferingType,category:String?,location:String?,minCapacity:Int?,maxPrice:BigDecimal?): List<MarketplaceOffering> = offerings.findAllByTypeAndStatus(type,OfferingStatus.ACTIVE).filter{category==null||it.category.equals(category,true)}.filter{location==null||it.location?.contains(location,true)==true}.filter{minCapacity==null||(it.capacity?:0)>=minCapacity}.filter{maxPrice==null||it.price<=maxPrice}
    @Transactional fun availability(userId:UUID,id:UUID,r:AvailabilityRequest):OfferingAvailability{ownOffering(userId,id);if(!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Período inválido");return availability.save(OfferingAvailability(offeringId=id,startsAt=r.startsAt,endsAt=r.endsAt,available=r.available))}
    @Transactional(readOnly=true) fun availability(id:UUID)=availability.findAllByOfferingId(id)
    @Transactional fun reserve(userId:UUID,r:ReservationRequest):Reservation{events.owned(userId,r.eventId);if(!r.endsAt.isAfter(r.startsAt))throw BadRequestException("Período inválido");val o=offerings.findById(r.offeringId).orElseThrow{NotFoundException("Publicación no encontrada")};if(o.status!=OfferingStatus.ACTIVE)throw ConflictException("La publicación no está activa");if(reservations.hasConflict(r.offeringId,r.startsAt,r.endsAt))throw ConflictException("El período ya está reservado");val ranges=availability.findAllByOfferingId(r.offeringId);if(ranges.isNotEmpty()&&ranges.none{it.available&&!r.startsAt.isBefore(it.startsAt)&&!r.endsAt.isAfter(it.endsAt)})throw ConflictException("El período no está disponible");return reservations.save(Reservation(eventId=r.eventId,offeringId=r.offeringId,requesterUserId=userId,startsAt=r.startsAt,endsAt=r.endsAt,note=r.note))}
    @Transactional(readOnly=true) fun eventReservations(userId:UUID,eventId:UUID):List<Reservation>{events.owned(userId,eventId);return reservations.findAllByEventId(eventId)}
    @Transactional fun decide(userId:UUID,id:UUID,r:DecisionRequest):Reservation{val res=findReservation(id);ownOffering(userId,res.offeringId);if(res.status!=ReservationStatus.PENDING)throw ConflictException("La reservación ya fue decidida");if(r.accepted&&reservations.hasConflict(res.offeringId,res.startsAt,res.endsAt))throw ConflictException("El período ya fue ocupado");res.status=if(r.accepted)ReservationStatus.ACCEPTED else ReservationStatus.REJECTED;res.decidedAt=Instant.now();return res}
    @Transactional fun cancel(userId:UUID,id:UUID):Reservation{val r=findReservation(id);val o=offerings.findById(r.offeringId).orElseThrow{NotFoundException("Publicación no encontrada")};if(r.requesterUserId!=userId&&o.ownerUserId!=userId)throw ForbiddenException("No puedes cancelar esta reservación");if(r.status in setOf(ReservationStatus.CANCELLED,ReservationStatus.COMPLETED))throw ConflictException("La reservación no se puede cancelar");r.status=ReservationStatus.CANCELLED;return r}
    @Transactional fun payment(userId:UUID,id:UUID,r:PaymentRequest):SimulatedPayment{val res=findReservation(id);if(res.requesterUserId!=userId)throw ForbiddenException("No puedes registrar este pago");if(r.amount<=BigDecimal.ZERO)throw BadRequestException("Monto inválido");return payments.save(SimulatedPayment(reservationId=id,amount=r.amount,status=r.status,reference="SIM-${UUID.randomUUID().toString().uppercase()}"))}
    @Transactional(readOnly=true) fun receipts(userId:UUID,id:UUID):List<SimulatedPayment>{val res=findReservation(id);val owner=offerings.findById(res.offeringId).map{it.ownerUserId}.orElse(null);if(res.requesterUserId!=userId&&owner!=userId)throw ForbiddenException("No puedes consultar estos comprobantes");return payments.findAllByReservationId(id)}
    @Transactional fun complete(userId:UUID,id:UUID):Reservation{val res=findReservation(id);ownOffering(userId,res.offeringId);if(res.status!=ReservationStatus.ACCEPTED)throw ConflictException("Solo una reservación aceptada puede concluir");res.status=ReservationStatus.COMPLETED;return res}
    @Transactional fun review(userId:UUID,id:UUID,r:ReviewRequest):Review{val res=findReservation(id);if(res.requesterUserId!=userId||res.status!=ReservationStatus.COMPLETED)throw ForbiddenException("La contratación no es elegible");if(r.rating !in 1..5)throw BadRequestException("Calificación inválida");if(reviews.existsByReservationIdAndAuthorUserId(id,userId))throw ConflictException("Ya calificaste esta contratación");return reviews.save(Review(reservationId=id,authorUserId=userId,rating=r.rating,comment=r.comment))}
    private fun ownOffering(userId:UUID,id:UUID)=offerings.findById(id).orElseThrow{NotFoundException("Publicación no encontrada")}.also{if(it.ownerUserId!=userId)throw ForbiddenException("No eres propietario")}
    private fun findReservation(id:UUID)=reservations.findById(id).orElseThrow{NotFoundException("Reservación no encontrada")}
}

@RestController @RequestMapping("/api") class MarketplaceController(private val s:MarketplaceService){
    @PostMapping("/offerings") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SPACE_OWNER','SERVICE_PROVIDER','ADMIN')") fun create(a:Authentication,@RequestBody r:OfferingRequest)=s.create(a.userId(),r)
    @PutMapping("/offerings/{id}") fun update(a:Authentication,@PathVariable id:UUID,@RequestBody r:OfferingRequest)=s.update(a.userId(),id,r)
    @PostMapping("/offerings/{id}/status/{status}") fun status(a:Authentication,@PathVariable id:UUID,@PathVariable status:OfferingStatus)=s.status(a.userId(),id,status)
    @GetMapping("/offerings") fun search(@RequestParam type:OfferingType,@RequestParam(required=false) category:String?,@RequestParam(required=false) location:String?,@RequestParam(required=false) minCapacity:Int?,@RequestParam(required=false) maxPrice:BigDecimal?)=s.search(type,category,location,minCapacity,maxPrice)
    @PostMapping("/offerings/{id}/availability") @ResponseStatus(HttpStatus.CREATED) fun availability(a:Authentication,@PathVariable id:UUID,@RequestBody r:AvailabilityRequest)=s.availability(a.userId(),id,r)
    @GetMapping("/offerings/{id}/availability") fun availability(@PathVariable id:UUID)=s.availability(id)
    @PostMapping("/reservations") @ResponseStatus(HttpStatus.CREATED) fun reserve(a:Authentication,@RequestBody r:ReservationRequest)=s.reserve(a.userId(),r)
    @GetMapping("/events/{eventId}/reservations") fun reservations(a:Authentication,@PathVariable eventId:UUID)=s.eventReservations(a.userId(),eventId)
    @PostMapping("/reservations/{id}/decision") fun decide(a:Authentication,@PathVariable id:UUID,@RequestBody r:DecisionRequest)=s.decide(a.userId(),id,r)
    @PostMapping("/reservations/{id}/cancel") fun cancel(a:Authentication,@PathVariable id:UUID)=s.cancel(a.userId(),id)
    @PostMapping("/reservations/{id}/payments") @ResponseStatus(HttpStatus.CREATED) fun pay(a:Authentication,@PathVariable id:UUID,@RequestBody r:PaymentRequest)=s.payment(a.userId(),id,r)
    @GetMapping("/reservations/{id}/payments") fun receipts(a:Authentication,@PathVariable id:UUID)=s.receipts(a.userId(),id)
    @PostMapping("/reservations/{id}/complete") fun complete(a:Authentication,@PathVariable id:UUID)=s.complete(a.userId(),id)
    @PostMapping("/reservations/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) fun review(a:Authentication,@PathVariable id:UUID,@RequestBody r:ReviewRequest)=s.review(a.userId(),id,r)
}
