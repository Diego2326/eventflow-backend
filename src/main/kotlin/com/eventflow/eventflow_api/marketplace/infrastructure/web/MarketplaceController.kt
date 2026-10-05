package com.eventflow.eventflow_api.marketplace.infrastructure.web

import com.eventflow.eventflow_api.marketplace.application.AvailabilityRequest
import com.eventflow.eventflow_api.marketplace.application.DecisionRequest
import com.eventflow.eventflow_api.marketplace.application.MarketplaceService
import com.eventflow.eventflow_api.marketplace.application.OfferingRequest
import com.eventflow.eventflow_api.marketplace.application.PaymentRequest
import com.eventflow.eventflow_api.marketplace.application.ReservationRequest
import com.eventflow.eventflow_api.marketplace.application.ReviewRequest
import com.eventflow.eventflow_api.marketplace.domain.OfferingStatus
import com.eventflow.eventflow_api.marketplace.domain.OfferingType
import com.eventflow.eventflow_api.shared.infrastructure.web.userId

import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@RestController @RequestMapping("/api") class MarketplaceController(private val s:MarketplaceService){
    @PostMapping("/offerings") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SPACE_OWNER','SERVICE_PROVIDER','ADMIN')") fun create(a:Authentication,@RequestBody r:OfferingRequest)=s.create(a.userId(),r)
    @PutMapping("/offerings/{id}") fun update(a:Authentication,@PathVariable id:UUID,@RequestBody r:OfferingRequest)=s.update(a.userId(),id,r)
    @PostMapping("/offerings/{id}/status/{status}") fun status(a:Authentication,@PathVariable id:UUID,@PathVariable status:OfferingStatus)=s.status(a.userId(),id,status)
    @GetMapping("/offerings") fun search(@RequestParam type:OfferingType,@RequestParam(required=false) category:String?,@RequestParam(required=false) location:String?,@RequestParam(required=false) minCapacity:Int?,@RequestParam(required=false) maxPrice:BigDecimal?,@RequestParam(defaultValue="50") limit:Int,
        @RequestParam(required=false) minRating:BigDecimal?,@RequestParam(required=false) feature:String?,
        @RequestParam(required=false) startsAt:Instant?,@RequestParam(required=false) endsAt:Instant?)=
        s.search(type,category,location,minCapacity,maxPrice,limit,minRating,feature,startsAt,endsAt)
    @PostMapping("/offerings/{id}/availability") @ResponseStatus(HttpStatus.CREATED) fun availability(a:Authentication,@PathVariable id:UUID,@RequestBody r:AvailabilityRequest)=s.availability(a.userId(),id,r)
    @GetMapping("/offerings/{id}/availability") fun availability(@PathVariable id:UUID)=s.availability(id)
    @PostMapping("/reservations") @ResponseStatus(HttpStatus.CREATED) fun reserve(a:Authentication,@RequestBody r:ReservationRequest)=s.reserve(a.userId(),r)
    @GetMapping("/events/{eventId}/reservations") fun reservations(a:Authentication,@PathVariable eventId:UUID)=s.eventReservations(a.userId(),eventId)
    @PostMapping("/reservations/{id}/decision") fun decide(a:Authentication,@PathVariable id:UUID,@RequestBody r:DecisionRequest)=s.decide(a.userId(),id,r)
    @PostMapping("/reservations/{id}/cancel") fun cancel(a:Authentication,@PathVariable id:UUID)=s.cancel(a.userId(),id)
    @PostMapping("/reservations/{id}/payments") @ResponseStatus(HttpStatus.CREATED) fun pay(a:Authentication,@PathVariable id:UUID,@RequestBody r:PaymentRequest)=s.payment(a.userId(),id,r)
    @GetMapping("/reservations/{id}/payments") fun receipts(a:Authentication,@PathVariable id:UUID)=s.receipts(a.userId(),id)
    @GetMapping("/reservations/{id}/payments/{paymentId}/receipt") fun receipt(a:Authentication,@PathVariable id:UUID,@PathVariable paymentId:UUID)=s.receipt(a.userId(),id,paymentId)
    @PostMapping("/reservations/{id}/complete") fun complete(a:Authentication,@PathVariable id:UUID)=s.complete(a.userId(),id)
    @PostMapping("/reservations/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) fun review(a:Authentication,@PathVariable id:UUID,@RequestBody r:ReviewRequest)=s.review(a.userId(),id,r)
}
