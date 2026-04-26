package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FinalAssessmentDto {
    private String id;
    private String trackName;
    private String title;
    private String status;          // PASSED | AVAILABLE | LOCKED | FAILED | COOLDOWN
    private String retakeAvailableAt; // ISO-8601 timestamp when cooldown ends, null if immediately available
    private Integer cooldownHours;    // hours of cooldown configured
    private Integer score;
    private Integer passingScore;
    private Integer attemptNumber;
    private String completedAt;     // ISO-8601 or null
    private Integer totalQuestions;
    private Integer answeredQuestions;
    private Boolean retakeAllowed;
    private Integer timeLimitMinutes;
    private String retakePolicy;    // UNLIMITED | number
    private AssessmentCertificateDto certificate;
    private AssessmentEligibilityDto eligibility;
    private List<TopicBreakdownDto> topicBreakdown;
}
