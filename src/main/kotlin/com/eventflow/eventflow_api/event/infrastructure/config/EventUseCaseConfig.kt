package com.eventflow.eventflow_api.event.infrastructure.config

import com.eventflow.eventflow_api.event.application.CreateEvent
import com.eventflow.eventflow_api.event.application.CreateEventGateway
import com.eventflow.eventflow_api.event.application.EventStatusGateway
import com.eventflow.eventflow_api.event.application.TransitionEvent

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EventUseCaseConfig {
    @Bean fun transitionEvent(gateway: EventStatusGateway) = TransitionEvent(gateway)
    @Bean fun createEvent(gateway: CreateEventGateway) = CreateEvent(gateway)
}
