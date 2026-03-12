package com.icentric.Icentric.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "modules", schema = "system")
public class CourseModule {

    @Id
    private UUID id;

    @Column(name = "track_id")
    private UUID trackId;

    private String title;

    private Integer sortOrder;

    private Boolean isPublished;

    private Instant createdAt;

    public CourseModule() {}
}
