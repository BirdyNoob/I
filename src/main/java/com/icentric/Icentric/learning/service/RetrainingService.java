package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RetrainingService {

    private final UserAssignmentRepository assignmentRepository;
    private final TrackRepository trackRepository;
    private final LessonProgressRepository progressRepository;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;

    public RetrainingService(
            UserAssignmentRepository assignmentRepository,
            TrackRepository trackRepository,
            LessonProgressRepository progressRepository,
            AuditService auditService,
            AuditMetadataService auditMetadataService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.trackRepository = trackRepository;
        this.progressRepository = progressRepository;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
    }

    @Transactional
    public void checkRetraining(UUID userId, UUID trackId) {

        var assignment = assignmentRepository
                .findByUserIdAndTrackId(userId, trackId)
                .orElseThrow();

        var track = trackRepository.findById(trackId)
                .orElseThrow();

        if (track.getVersion() > assignment.getContentVersionAtAssignment()) {

            // 🔥 mark retraining
            assignment.setRequiresRetraining(true);
            assignment.setStatus(AssignmentStatus.ASSIGNED);

            assignmentRepository.save(assignment);
            auditService.log(
                    userId,
                    AuditAction.RETRAINING_REQUIRED,
                    "ASSIGNMENT",
                    assignment.getId().toString(),
                    auditMetadataService.describeUserInCurrentTenant(userId)
                            + " requires retraining on "
                            + auditMetadataService.describeTrack(trackId)
                            + " because content advanced from version "
                            + assignment.getContentVersionAtAssignment() + " to " + track.getVersion()
                            + ". Assignment reset for retraining."
            );

            // 🔥 reset progress
            progressRepository.deleteByUserIdAndTrackId(userId, trackId);
            auditService.log(
                    userId,
                    AuditAction.RETRAINING_PROGRESS_RESET,
                    "ASSIGNMENT",
                    assignment.getId().toString(),
                    "Cleared lesson progress for "
                            + auditMetadataService.describeUserInCurrentTenant(userId)
                            + " on " + auditMetadataService.describeTrack(trackId)
            );
        }
    }
}
