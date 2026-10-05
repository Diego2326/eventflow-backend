package com.eventflow.eventflow_api.infrastructure.web.marketplace

import com.eventflow.eventflow_api.infrastructure.web.common.*
import com.eventflow.eventflow_api.application.marketplace.*
import com.eventflow.eventflow_api.domain.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

@RestController @RequestMapping("/api") class MarketplaceController(private val s:MarketplaceService){
    @PostMapping("/offerings") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SPACE_OWNER','SERVICE_PROVIDER','ADMIN')") fun create(a:Authentication,@RequestBody r:OfferingRequest)=s.create(a.userId(),r)
    @PutMapping("/offerings/{id}") fun update(a:Authentication,@PathVariable id:UUID,@RequestBody r:OfferingRequest)=s.update(a.userId(),id,r)
    @PostMapping("/offerings/{id}/status/{status}") fun status(a:Authentication,@PathVariable id:UUID,@PathVariable status:OfferingStatus)=s.status(a.userId(),id,status)
    @GetMapping("/offerings") fun search(@RequestParam type:OfferingType,@RequestParam(required=false) category:String?,@RequestParam(required=false) location:String?,@RequestParam(required=false) minCapacity:Int?,@RequestParam(required=false) maxPrice:BigDecimal?,@RequestParam(defaultValue="50") limit:Int)=s.search(type,category,location,minCapacity,maxPrice,limit)
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
