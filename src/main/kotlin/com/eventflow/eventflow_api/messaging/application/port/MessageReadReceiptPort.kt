package com.eventflow.eventflow_api.messaging.application.port

import com.eventflow.eventflow_api.messaging.domain.MessageReadReceipt
import java.util.UUID

interface MessageReadReceiptPort{
    fun findAllByMessageIdIn(messageIds:Collection<UUID>):List<MessageReadReceipt>
    fun findByMessageIdAndUserId(messageId:UUID,userId:UUID):MessageReadReceipt?
    fun save(receipt:MessageReadReceipt):MessageReadReceipt
}
