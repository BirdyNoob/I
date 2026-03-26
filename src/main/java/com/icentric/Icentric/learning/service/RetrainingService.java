package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RetrainingService {

    private final UserAssignmentRepository assignmentRepository;
    private final TrackRepository trackRepository;
    private final LessonProgressRepository progressRepository;

    public RetrainingService(
            UserAssignmentRepository assignmentRepository,
            TrackRepository trackRepository,
            LessonProgressRepository progressRepository
    ) {
        this.assignmentRepository = assignmentRepository;
        this.trackRepository = trackRepository;
        this.progressRepository = progressRepository;
    }

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

            // 🔥 reset progress
            progressRepository.deleteByUserIdAndTrackId(userId, trackId);
        }
    }
}
