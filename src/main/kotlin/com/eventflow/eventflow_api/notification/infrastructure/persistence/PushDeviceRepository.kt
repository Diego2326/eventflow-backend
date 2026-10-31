package com.eventflow.eventflow_api.notification.infrastructure.persistence

import com.eventflow.eventflow_api.notification.domain.PushDevice
import com.eventflow.eventflow_api.notification.application.port.PushDevicePort
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PushDeviceRepository : JpaRepository<PushDevice, UUID>, PushDevicePort
