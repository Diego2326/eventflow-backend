package com.eventflow.eventflow_api.infrastructure.config

import com.eventflow.eventflow_api.application.event.EventStatusGateway
import com.eventflow.eventflow_api.application.event.TransitionEvent
import com.eventflow.eventflow_api.application.event.CreateEvent
import com.eventflow.eventflow_api.application.event.CreateEventGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EventUseCaseConfig {
    @Bean fun transitionEvent(gateway: EventStatusGateway) = TransitionEvent(gateway)
    @Bean fun createEvent(gateway: CreateEventGateway) = CreateEvent(gateway)
}
