package com.icentric.Icentric.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "answers", schema = "system")
public class Answer {

    @Id
    private UUID id;

    private UUID questionId;

    private String answerText;

    private Boolean isCorrect;

}
