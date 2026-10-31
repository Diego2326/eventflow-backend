package com.eventflow.eventflow_api.event.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name="event_template")
class StoredEventTemplate(
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="event_template_id") var id:UUID?=null,
    @Column(name="owner_user_id",nullable=false) var ownerUserId:UUID,
    @Column(name="template_name",nullable=false,length=120) var name:String,
    @Column(name="event_type",nullable=false,length=40) var eventType:String,
    @Column(columnDefinition="text") var description:String?=null,
    @Column(name="module_codes",nullable=false,columnDefinition="text") var moduleCodes:String="[]",
    @Column(name="created_at",nullable=false) var createdAt:Instant=Instant.now(),
    @Column(name="updated_at",nullable=false) var updatedAt:Instant=Instant.now()
)
