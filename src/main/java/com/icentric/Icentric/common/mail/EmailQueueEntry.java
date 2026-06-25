package com.icentric.Icentric.common.mail;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "email_queue", schema = "system")
public class EmailQueueEntry {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb", nullable = false)
    private String variables;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void onCreate() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
}
