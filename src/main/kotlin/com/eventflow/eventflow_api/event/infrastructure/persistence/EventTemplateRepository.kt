package com.eventflow.eventflow_api.event.infrastructure.persistence

import com.eventflow.eventflow_api.event.application.port.EventTemplateRepositoryPort
import com.eventflow.eventflow_api.event.domain.StoredEventTemplate
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EventTemplateRepository:JpaRepository<StoredEventTemplate,UUID>,EventTemplateRepositoryPort{
    override fun findAllByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId:UUID):List<StoredEventTemplate>
    override fun findByOwnerUserIdAndId(ownerUserId:UUID,id:UUID):StoredEventTemplate?
}
