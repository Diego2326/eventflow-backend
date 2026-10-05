package com.eventflow.eventflow_api.storage.infrastructure.storage

import com.eventflow.eventflow_api.storage.application.ObjectStorage

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SupabaseObjectStorage(
    @Value("\${app.storage.url:}") private val url: String,
    @Value("\${app.storage.service-key:}") private val key: String,
    @Value("\${app.storage.bucket:eventflow}") private val bucket: String
) : ObjectStorage {
    override val configured: Boolean get() = url.isNotBlank() && key.isNotBlank()

    override fun put(path: String, contentType: String, bytes: ByteArray) {
        client().post().uri("/storage/v1/object/{bucket}/{path}", bucket, path)
            .contentType(MediaType.parseMediaType(contentType)).header("x-upsert", "false")
            .body(bytes).retrieve().toBodilessEntity()
    }

    override fun get(path: String): ByteArray = client().get()
        .uri("/storage/v1/object/{bucket}/{path}", bucket, path)
        .retrieve().body(ByteArray::class.java) ?: ByteArray(0)

    override fun delete(path: String) {
        client().delete().uri("/storage/v1/object/{bucket}/{path}", bucket, path)
            .retrieve().toBodilessEntity()
    }

    private fun client() = RestClient.builder().baseUrl(url.trimEnd('/'))
        .defaultHeader("Authorization", "Bearer $key").defaultHeader("apikey", key).build()
}
