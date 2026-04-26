package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.assessment.AssessmentDashboardResponse;
import com.icentric.Icentric.learning.dto.assessment.AssessmentDataResponse;
import com.icentric.Icentric.learning.dto.assessment.AssessmentRenderResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.icentric.Icentric.learning.dto.assessment.CreateAssessmentConfigRequest;

import com.icentric.Icentric.learning.dto.assessment.SubmitAssessmentRequest;
import com.icentric.Icentric.learning.dto.assessment.SubmitAssessmentResponse;
import com.icentric.Icentric.learning.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/learner/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            throw new IllegalStateException("Unauthenticated request");
        }
        return UUID.fromString(auth.getDetails().toString());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AssessmentDashboardResponse> getAssessmentDashboard() {
        AssessmentDashboardResponse response = assessmentService.getAssessmentDashboard(currentUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate/{trackId}")
    public ResponseEntity<AssessmentDataResponse> generateAssessment(@PathVariable String trackId) {
        AssessmentDataResponse response = assessmentService.generateAssessment(trackId, currentUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{assessmentId}/render")
    public ResponseEntity<AssessmentRenderResponse> renderAssessment(@PathVariable String assessmentId) {
        AssessmentRenderResponse response = assessmentService.getAssessmentForRender(assessmentId, currentUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{assessmentId}/submit")
    public ResponseEntity<SubmitAssessmentResponse> submitAssessment(
            @PathVariable String assessmentId,
            @RequestBody SubmitAssessmentRequest request) {
        SubmitAssessmentResponse response = assessmentService.submitAssessment(assessmentId, request, currentUserId());
        return ResponseEntity.ok(response);
    }

}
