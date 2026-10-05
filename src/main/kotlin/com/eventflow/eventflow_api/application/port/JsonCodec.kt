package com.eventflow.eventflow_api.application.port

interface JsonCodec {
    fun write(value: Any): String
    fun readMap(json: String): Map<String, Any?>
    fun readStringList(json: String): List<String>
}
