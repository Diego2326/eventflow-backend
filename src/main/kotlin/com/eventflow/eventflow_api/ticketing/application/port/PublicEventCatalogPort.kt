package com.eventflow.eventflow_api.ticketing.application.port

import com.eventflow.eventflow_api.event.domain.EventEntity
import java.time.Instant

interface PublicEventCatalogPort {
    fun search(q: String?, type: String?, from: Instant?, offset: Int, limit: Int): List<EventEntity>
}
