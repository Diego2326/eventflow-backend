package com.eventflow.eventflow_api.module.application

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component class SurveyAnonymity(@Value("\${app.survey-anonymity-key}") private val key:String){
    init { require(key.isNotBlank()) { "La clave de encuestas anónimas no puede estar vacía" } }
    fun participantHash(surveyId:UUID,userId:UUID):String{
        val mac=Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8),"HmacSHA256"))
        return mac.doFinal("$surveyId:$userId".toByteArray(StandardCharsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
}
