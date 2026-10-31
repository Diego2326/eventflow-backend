package com.eventflow.eventflow_api.storage.application

import java.time.Duration

data class StoredObjectMetadata(val sizeBytes: Long, val contentType: String?)

interface DirectObjectStorage {
    fun createUploadUrl(path: String, contentType: String, lifetime: Duration): String
    fun createDownloadUrl(path: String, lifetime: Duration): String
    fun metadata(path: String): StoredObjectMetadata?
}
