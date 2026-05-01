package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopicBreakdownDto {
    private String stepId;
    private String lessonId;
    private String title;
    private String status;    // COMPLETED | IN_PROGRESS | NOT_STARTED
    private String meta;      // "Done" | "In Progress" | "Not Started"
    private String actionLabel; // "Review" | "Continue" | "Start"
}
