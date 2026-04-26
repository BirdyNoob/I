package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.learning.dto.assessment.AssessmentDashboardResponse;
import com.icentric.Icentric.learning.dto.assessment.AssessmentDataResponse;
import com.icentric.Icentric.learning.dto.assessment.AssessmentRenderResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.icentric.Icentric.learning.dto.assessment.CreateAssessmentConfigRequest;

import com.icentric.Icentric.learning.dto.assessment.SubmitAssessmentRequest;
import com.icentric.Icentric.learning.dto.assessment.SubmitAssessmentResponse;

import java.util.UUID;

public interface AssessmentService {
    AssessmentDashboardResponse getAssessmentDashboard(UUID userId);
    AssessmentDataResponse generateAssessment(String trackId, UUID userId);
    AssessmentRenderResponse getAssessmentForRender(String assessmentId, UUID userId);
    SubmitAssessmentResponse submitAssessment(String assessmentId, SubmitAssessmentRequest request, UUID userId);
    
    void createAssessmentConfig(String trackId, JsonNode request);
    
    java.util.List<com.icentric.Icentric.learning.dto.assessment.AdminAssessmentConfigDto> getAllAssessmentConfigs();
}
