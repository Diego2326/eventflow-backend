package com.eventflow.eventflow_api.event.infrastructure.persistence

import com.eventflow.eventflow_api.event.application.port.EventRepositoryPort
import com.eventflow.eventflow_api.event.domain.EventCollaborator
import com.eventflow.eventflow_api.event.domain.EventEntity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface EventRepository : JpaRepository<EventEntity, UUID>, EventRepositoryPort {
    @Query("select distinct e from EventEntity e left join EventCollaborator c on c.eventId=e.id where e.ownerUserId=:userId or c.userId=:userId")
    override fun findAccessible(userId: UUID): List<EventEntity>
}
