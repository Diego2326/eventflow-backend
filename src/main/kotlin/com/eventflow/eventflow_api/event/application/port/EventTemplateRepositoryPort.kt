package com.eventflow.eventflow_api.event.application.port

import com.eventflow.eventflow_api.event.domain.StoredEventTemplate
import com.eventflow.eventflow_api.shared.application.port.CrudPort
import java.util.UUID

interface EventTemplateRepositoryPort:CrudPort<StoredEventTemplate,UUID>{
    fun findAllByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId:UUID):List<StoredEventTemplate>
    fun findByOwnerUserIdAndId(ownerUserId:UUID,id:UUID):StoredEventTemplate?
}
