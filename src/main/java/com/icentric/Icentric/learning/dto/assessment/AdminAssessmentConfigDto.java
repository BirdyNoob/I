package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AdminAssessmentConfigDto {
    private String id;
    private String trackId;
    private String title;
    private String trackName;
    private JsonNode config;
    private Instant createdAt;
    private Instant updatedAt;
}
