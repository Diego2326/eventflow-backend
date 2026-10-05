package com.eventflow.eventflow_api.invitation.infrastructure.web

import com.eventflow.eventflow_api.invitation.application.CheckRequest
import com.eventflow.eventflow_api.invitation.application.CreateInvitationRequest
import com.eventflow.eventflow_api.invitation.application.GuestAssistanceRequest
import com.eventflow.eventflow_api.invitation.application.InvitationService
import com.eventflow.eventflow_api.invitation.application.LinkInvitationsRequest
import com.eventflow.eventflow_api.invitation.application.QrCheckRequest
import com.eventflow.eventflow_api.invitation.application.RsvpRequest
import com.eventflow.eventflow_api.invitation.application.SeatAssignmentRequest
import com.eventflow.eventflow_api.shared.infrastructure.web.userId

import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api") class InvitationController(private val service:InvitationService){
    @PostMapping("/events/{eventId}/invitations") @ResponseStatus(HttpStatus.CREATED) fun create(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:CreateInvitationRequest)=service.create(a.userId(),eventId,r)
    @GetMapping("/events/{eventId}/invitations") fun list(a:Authentication,@PathVariable eventId:UUID)=service.list(a.userId(),eventId)
    @PostMapping("/events/{eventId}/invitations/{id}/revoke") fun revoke(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.revoke(a.userId(),eventId,id)
    @PostMapping("/events/{eventId}/invitations/{id}/regenerate") fun regenerate(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID)=service.regenerate(a.userId(),eventId,id)
    @PatchMapping("/events/{eventId}/invitations/{id}/seat") fun assignSeat(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:SeatAssignmentRequest)=service.assignSeat(a.userId(),eventId,id,r)
    @PostMapping("/events/{eventId}/invitations/{id}/check-in") fun checkin(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:CheckRequest)=service.check(a.userId(),eventId,id,r,true)
    @PostMapping("/events/{eventId}/invitations/{id}/check-out") fun checkout(a:Authentication,@PathVariable eventId:UUID,@PathVariable id:UUID,@RequestBody r:CheckRequest)=service.check(a.userId(),eventId,id,r,false)
    @PostMapping("/events/{eventId}/check-in") fun checkinQr(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:QrCheckRequest)=service.checkQr(a.userId(),eventId,r,true)
    @PostMapping("/events/{eventId}/check-out") fun checkoutQr(a:Authentication,@PathVariable eventId:UUID,@RequestBody r:QrCheckRequest)=service.checkQr(a.userId(),eventId,r,false)
    @GetMapping("/invitations/access/{token}") fun access(@PathVariable token:String)=service.access(token)
    @GetMapping("/invitations/access/{token}/experience") fun experience(@PathVariable token:String)=service.experience(token)
    @PostMapping("/invitations/access/{token}/rsvp") fun rsvp(@PathVariable token:String,@RequestBody r:RsvpRequest)=service.rsvp(token,r)
    @PostMapping("/invitations/access/{token}/assistance") @ResponseStatus(HttpStatus.CREATED) fun assistance(@PathVariable token:String,@RequestBody r:GuestAssistanceRequest)=service.assistance(token,r)
    @GetMapping("/invitations/access/{token}/assistance") fun assistanceHistory(@PathVariable token:String)=service.assistanceHistory(token)
    @PostMapping("/invitations/access/{token}/link") fun link(a:Authentication,@PathVariable token:String)=service.link(a.userId(),token)
    @PostMapping("/invitations/link") fun linkAll(a:Authentication,@RequestBody r:LinkInvitationsRequest)=service.linkAll(a.userId(),r.tokens)
    @GetMapping("/invitations/mine") fun mine(a:Authentication)=service.mine(a.userId())
    @GetMapping("/invitations/{id}/experience") fun linkedExperience(a:Authentication,@PathVariable id:UUID)=service.linkedExperience(a.userId(),id)
    @PostMapping("/invitations/{id}/rsvp") fun linkedRsvp(a:Authentication,@PathVariable id:UUID,@RequestBody r:RsvpRequest)=service.linkedRsvp(a.userId(),id,r)
    @PostMapping("/invitations/{id}/assistance") @ResponseStatus(HttpStatus.CREATED) fun linkedAssistance(a:Authentication,@PathVariable id:UUID,@RequestBody r:GuestAssistanceRequest)=service.linkedAssistance(a.userId(),id,r)
    @GetMapping("/invitations/{id}/assistance") fun linkedAssistanceHistory(a:Authentication,@PathVariable id:UUID)=service.linkedAssistanceHistory(a.userId(),id)
}
