package com.icentric.Icentric.content.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    private String lessonType;

    @Column(name = "content_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String contentJson;

    private String videoUrl;

    private String resourceUrl;

    private Integer sortOrder;

    private Boolean isPublished;

    private Instant createdAt;

    public Lesson() {}

    // getters setters
}
