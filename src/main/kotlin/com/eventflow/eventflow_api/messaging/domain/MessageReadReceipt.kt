package com.eventflow.eventflow_api.messaging.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity @Table(name="message_read_receipt",uniqueConstraints=[UniqueConstraint(name="uq_message_read_user_message",columnNames=["message_id","user_id"])])
class MessageReadReceipt(
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="message_read_receipt_id") var id:UUID?=null,
    @Column(name="message_id",nullable=false) var messageId:UUID,
    @Column(name="user_id",nullable=false) var userId:UUID,
    @Column(name="read_at",nullable=false) var readAt:Instant=Instant.now()
)
