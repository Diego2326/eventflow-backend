package com.eventflow.eventflow_api.shared.infrastructure.serialization

import com.eventflow.eventflow_api.shared.application.port.JsonCodec

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class JacksonJsonCodec(private val mapper: ObjectMapper) : JsonCodec {
    override fun write(value: Any): String = mapper.writeValueAsString(value)

    @Suppress("UNCHECKED_CAST")
    override fun readMap(json: String): Map<String, Any?> =
        mapper.readValue(json, Map::class.java) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    override fun readStringList(json: String): List<String> =
        mapper.readValue(json, List::class.java) as List<String>
}
