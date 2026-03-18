package com.icentric.Icentric.learning.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {

    @Id
    private UUID id;

    private UUID attemptId;
    private UUID questionId;
    private UUID answerId;

    private Boolean isCorrect;
}