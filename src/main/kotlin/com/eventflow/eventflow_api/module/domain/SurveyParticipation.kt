package com.eventflow.eventflow_api.module.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity @Table(name="survey_participation") class SurveyParticipation(
    @Id @Column(name="participant_hash",length=64) var participantHash:String="",
    @Column(name="survey_id",nullable=false) var surveyId:UUID,
    @Column(name="response_id",nullable=false,unique=true) var responseId:UUID
)
