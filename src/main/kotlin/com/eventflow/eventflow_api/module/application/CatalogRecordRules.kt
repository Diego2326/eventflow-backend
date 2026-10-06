package com.eventflow.eventflow_api.module.application

import com.eventflow.eventflow_api.shared.application.error.BadRequestException
import java.net.URI
import java.util.UUID

/** Field rules for published catalog records that share the modular record store. */
object CatalogRecordRules {
    fun validate(module: String, type: String, title: String?, payload: Map<String, Any?>) {
        if (module to type !in catalogTypes) return
        if (title.isNullOrBlank() || title.length > 200) throw BadRequestException("El registro requiere un nombre de hasta 200 caracteres")
        listOf("description", "bio", "organization", "category", "location", "meetingPoint", "origin", "destination")
            .forEach { key ->
                val value = payload[key] ?: return@forEach
                if (value !is String || value.isBlank() || value.length > 2000) throw BadRequestException("$key inválido")
            }
        if (module == "TRN" && type == "ROUTE") {
            if (payload["origin"] !is String || payload["destination"] !is String || payload["meetingPoint"] !is String)
                throw BadRequestException("La ruta requiere origen, destino y punto de encuentro")
        }
        if (module == "TRN" && type == "DEPARTURE" && payload["requiresReservation"] != null && payload["requiresReservation"] !is Boolean)
            throw BadRequestException("Configuración de reserva inválida")
        if (module == "RSC" && type == "RESOURCE") {
            val url = payload["url"]
            val fileId = payload["fileId"]
            if (url == null && fileId == null) throw BadRequestException("El recurso requiere enlace o archivo")
            if (url != null) {
                val parsed = if (url is String && url.length <= 2048) runCatching { URI(url) }.getOrNull() else null
                if (parsed?.scheme?.lowercase() != "https" || parsed.host.isNullOrBlank() || parsed.userInfo != null)
                    throw BadRequestException("El recurso requiere un enlace HTTPS válido")
            }
            if (fileId != null && runCatching { UUID.fromString(fileId.toString()) }.isFailure)
                throw BadRequestException("Archivo de recurso inválido")
        }
        if (module == "SPT" && type == "BRACKET" && payload["rounds"] != null) {
            val rounds = payload["rounds"] as? Number ?: throw BadRequestException("Rondas inválidas")
            if (rounds.toInt() !in 1..32 || rounds.toDouble() != rounds.toInt().toDouble())
                throw BadRequestException("Rondas inválidas")
        }
    }

    private val catalogTypes = setOf(
        "EXH" to "EXHIBITOR", "EXH" to "STAND", "SES" to "SPEAKER", "SES" to "SESSION",
        "TRN" to "ROUTE", "TRN" to "DEPARTURE", "SPT" to "TEAM", "SPT" to "PARTICIPANT",
        "SPT" to "BRACKET", "RSC" to "RESOURCE"
    )
}
