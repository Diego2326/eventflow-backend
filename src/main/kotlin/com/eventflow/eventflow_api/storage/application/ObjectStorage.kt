package com.eventflow.eventflow_api.storage.application

data class StoredFile(val bytes: ByteArray, val contentType: String, val fileName: String)

interface ObjectStorage {
    val configured: Boolean
    fun put(path: String, contentType: String, bytes: ByteArray)
    fun get(path: String): ByteArray
    fun delete(path: String)
}
