package com.eventflow.eventflow_api.event.domain

object EventTemplates {
    private val defaults = mapOf(
        "WEDDING" to listOf("INV","GST","CAL","MAP","ORD","AST","GAL","INT","TRN","NOT"),
        "BIRTHDAY" to listOf("INV","GST","CAL","MAP","GAL","INT","GAM","ORD","NOT"),
        "GRADUATION" to listOf("INV","GST","CAL","MAP","GAL","RSC","NOT"),
        "CONFERENCE" to listOf("INV","GST","CAL","MAP","SES","INT","NET","RSC","QUE","NOT"),
        "CORPORATE" to listOf("INV","GST","CAL","MAP","NET","INT","RSC","NOT"),
        "EXPO" to listOf("INV","GST","MAP","EXH","GAM","QUE","NET","RSC","NOT"),
        "JOB_FAIR" to listOf("INV","GST","MAP","EXH","NET","QUE","RSC","NOT"),
        "FESTIVAL" to listOf("INV","GST","CAL","MAP","QUE","AFO","TRN","LNF","NOT"),
        "TOURNAMENT" to listOf("INV","GST","CAL","MAP","SPT","INT","AFO","NOT"),
        "HACKATHON" to listOf("INV","GST","CAL","MAP","NET","INT","RSC","BKG","NOT"),
        "WORKSHOP" to listOf("INV","GST","CAL","SES","INT","RSC","NOT"),
        "CAMP" to listOf("INV","GST","CAL","MAP","TRN","AST","AFO","NOT"),
        "TRIP" to listOf("INV","GST","CAL","MAP","TRN","NOT"),
        "GALA" to listOf("INV","GST","CAL","MAP","ORD","INT","GAL","NOT")
    )

    fun modulesFor(type: String): List<String> = defaults[type].orEmpty()
}
