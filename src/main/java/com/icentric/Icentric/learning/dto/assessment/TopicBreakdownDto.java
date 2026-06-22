package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopicBreakdownDto {
    private String moduleId;
    private String title;
    private String status;      // COMPLETED | IN_PROGRESS | NOT_STARTED
    private String meta;        // "5/5 lessons" | "3/5 lessons" | "Not Started"
    private String actionLabel; // "Review" | "Continue" | "Start"
}
