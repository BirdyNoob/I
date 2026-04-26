package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleQuizDto {
    private String id;
    private String moduleName;
    private String quizTitle;
    private String status;
    private Integer score;
    private String dateCompleted;
    private List<String> topics;
    private QuizDetailsDto details;
}
