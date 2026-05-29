package com.icentric.Icentric.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "xp_transaction_log")
public class XpTransactionLog {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "xp_granted", nullable = false)
    private Integer xpGranted;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "reference_entity_type")
    private String referenceEntityType;

    @Column(name = "reference_entity_id")
    private UUID referenceEntityId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public XpTransactionLog() {}
}
