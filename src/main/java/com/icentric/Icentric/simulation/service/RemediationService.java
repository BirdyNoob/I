package com.icentric.Icentric.simulation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemediationService {

    // Note: In a real environment, you would inject AssignmentService here
    // private final AssignmentService assignmentService;

    public void triggerMandatoryRetraining(UUID userId, UUID simulationId) {
        log.warn("CRITICAL security violation by user {}. Triggering remediation for simulation {}", userId, simulationId);
        
        // This connects to the existing AssignmentService to trigger a mandatory course
        // assignmentService.assignMandatoryRemediationTrack(userId, "AI_SAFETY_BASICS");
    }
}
