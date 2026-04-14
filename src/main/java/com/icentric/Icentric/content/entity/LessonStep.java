package com.icentric.Icentric.content.entity;

import com.icentric.Icentric.content.constants.StepType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "lesson_steps", schema = "system")
public class LessonStep {

    @Id
    private UUID id;

    @Column(name = "lesson_id")
    private UUID lessonId;

    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type")
    private StepType stepType;

    @Column(name = "content_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String contentJson;

    private Integer sortOrder;

    private Instant createdAt;

    public LessonStep() {}

}
