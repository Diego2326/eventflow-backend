package com.eventflow.eventflow_api.messaging.infrastructure.persistence

import com.eventflow.eventflow_api.messaging.application.port.MessageReadReceiptPort
import com.eventflow.eventflow_api.messaging.domain.MessageReadReceipt
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MessageReadReceiptRepository:JpaRepository<MessageReadReceipt,UUID>,MessageReadReceiptPort{
    override fun findAllByMessageIdIn(messageIds:Collection<UUID>):List<MessageReadReceipt>
    override fun findByMessageIdAndUserId(messageId:UUID,userId:UUID):MessageReadReceipt?
}
