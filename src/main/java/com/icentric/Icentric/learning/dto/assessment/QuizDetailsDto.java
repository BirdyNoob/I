package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuizDetailsDto {
    private Integer correctCount;
    private Integer totalCount;
    private List<QuizQuestionDto> questions;
}
