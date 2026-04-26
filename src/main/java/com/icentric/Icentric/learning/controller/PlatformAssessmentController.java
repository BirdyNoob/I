package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/assessments")
@RequiredArgsConstructor
public class PlatformAssessmentController {

    private final AssessmentService assessmentService;

    @PostMapping("/config/{trackId}")
    public ResponseEntity<Void> createAssessmentConfig(
            @PathVariable String trackId,
            @RequestBody java.util.Map<String, Object> requestMap) {
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode requestNode = mapper.valueToTree(requestMap);
        
        assessmentService.createAssessmentConfig(trackId, requestNode);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping
    public ResponseEntity<java.util.List<com.icentric.Icentric.learning.dto.assessment.AdminAssessmentConfigDto>> getAllAssessments() {
        return ResponseEntity.ok(assessmentService.getAllAssessmentConfigs());
    }
}
