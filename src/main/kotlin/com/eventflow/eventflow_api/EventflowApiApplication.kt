package com.eventflow.eventflow_api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class EventflowApiApplication

fun main(args: Array<String>) {
	runApplication<EventflowApiApplication>(*args)
}
