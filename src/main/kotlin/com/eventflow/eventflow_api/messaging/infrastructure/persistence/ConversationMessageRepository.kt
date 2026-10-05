package com.eventflow.eventflow_api.messaging.infrastructure.persistence

import com.eventflow.eventflow_api.marketplace.domain.MarketplaceOffering
import com.eventflow.eventflow_api.marketplace.domain.Reservation
import com.eventflow.eventflow_api.messaging.application.port.ConversationMessageRepositoryPort
import com.eventflow.eventflow_api.messaging.domain.ConversationMessage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ConversationMessageRepository : JpaRepository<ConversationMessage, UUID>, ConversationMessageRepositoryPort {
    override fun findAllByEventIdAndChannelOrderByCreatedAt(eventId: UUID, channel: String): List<ConversationMessage>
    @Query("select m from ConversationMessage m where m.eventId=:eventId and m.channel=:channel and " +
        "(m.senderUserId=:userId or m.recipientUserId=:userId or exists " +
        "(select r.id from Reservation r, MarketplaceOffering o where r.id=m.reservationId " +
        "and o.id=r.offeringId and (r.requesterUserId=:userId or o.ownerUserId=:userId))) " +
        "order by m.createdAt")
    override fun findVisible(eventId: UUID, channel: String, userId: UUID): List<ConversationMessage>
}
