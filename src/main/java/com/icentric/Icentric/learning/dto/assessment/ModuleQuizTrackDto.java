package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ModuleQuizTrackDto {
    private String trackId;
    private String trackName;
    private List<ModuleQuizDto> quizzes;
}
