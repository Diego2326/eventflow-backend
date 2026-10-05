package com.eventflow.eventflow_api.module.application.port

import com.eventflow.eventflow_api.module.domain.SurveyParticipation
import com.eventflow.eventflow_api.shared.application.port.CrudPort

interface SurveyParticipationRepositoryPort:CrudPort<SurveyParticipation,String>
