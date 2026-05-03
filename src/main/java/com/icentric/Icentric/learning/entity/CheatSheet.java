package com.icentric.Icentric.learning.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Data
@Entity
@Table(name = "cheat_sheets", schema = "system")
public class CheatSheet {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(name = "type_code", nullable = false)
    private String type;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private com.icentric.Icentric.common.enums.Department department;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    private JsonNode data;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
