package com.eventflow.eventflow_api.ticketing.infrastructure.persistence

import com.eventflow.eventflow_api.event.domain.EventEntity
import com.eventflow.eventflow_api.event.domain.EventStatus
import com.eventflow.eventflow_api.event.domain.EventVisibility
import com.eventflow.eventflow_api.ticketing.application.port.PublicEventCatalogPort
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JpaPublicEventCatalog(private val em: EntityManager) : PublicEventCatalogPort {
    override fun search(q: String?, type: String?, from: Instant?, offset: Int, limit: Int): List<EventEntity> {
        val query = StringBuilder("select e from EventEntity e where e.visibility = :visibility and e.status in :statuses")
        if (!q.isNullOrBlank()) query.append(" and (lower(e.name) like :q or lower(e.description) like :q)")
        if (!type.isNullOrBlank()) query.append(" and lower(e.type) = :type")
        if (from != null) query.append(" and e.startsAt >= :from")
        query.append(" order by e.startsAt, e.id")
        val typed = em.createQuery(query.toString(), EventEntity::class.java)
            .setParameter("visibility", EventVisibility.PUBLIC)
            .setParameter("statuses", listOf(EventStatus.PUBLISHED, EventStatus.RUNNING))
            .setFirstResult(offset).setMaxResults(limit)
        if (!q.isNullOrBlank()) typed.setParameter("q", "%${q.trim().lowercase()}%")
        if (!type.isNullOrBlank()) typed.setParameter("type", type.trim().lowercase())
        if (from != null) typed.setParameter("from", from)
        return typed.resultList
    }
}
