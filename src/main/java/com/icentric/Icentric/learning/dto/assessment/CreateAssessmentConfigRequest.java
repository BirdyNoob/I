package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class CreateAssessmentConfigRequest {
    private String id;
    private String trackId;
    private JsonNode configData;
}
