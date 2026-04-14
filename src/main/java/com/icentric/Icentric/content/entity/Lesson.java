package com.icentric.Icentric.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;
@Data
@Entity
@Table(name = "lessons", schema = "system")
public class Lesson {

    @Id
    private UUID id;

    @Column(name = "module_id")
    private UUID moduleId;

    private String title;

    private Integer estimatedMins;

    private Integer sortOrder;

    private Boolean isPublished;

    private Instant createdAt;

    public Lesson() {}

    // getters setters
}
