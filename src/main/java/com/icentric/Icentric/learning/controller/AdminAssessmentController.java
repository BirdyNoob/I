package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/assessments")
@RequiredArgsConstructor
public class AdminAssessmentController {

    private final AssessmentService assessmentService;

    @GetMapping
    public ResponseEntity<java.util.List<com.icentric.Icentric.learning.dto.assessment.AdminAssessmentConfigDto>> getAllAssessments() {
        return ResponseEntity.ok(assessmentService.getAllAssessmentConfigs());
    }
}
