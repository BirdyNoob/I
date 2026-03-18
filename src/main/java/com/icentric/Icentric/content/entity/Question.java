package com.icentric.Icentric.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "questions", schema = "system")
public class Question {

    @Id
    private UUID id;

    private UUID lessonId;

    private String questionText;

    private String questionType;

    private Instant createdAt;

}
