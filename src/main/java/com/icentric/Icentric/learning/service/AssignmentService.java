package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AssignmentService {

    private final UserAssignmentRepository repository;
    private final TrackRepository trackRepository;

    public AssignmentService(
            UserAssignmentRepository repository, TrackRepository trackRepository
    ) {
        this.repository = repository;
        this.trackRepository = trackRepository;
    }

    public UserAssignment assignTrack(CreateAssignmentRequest request) {
        var track = trackRepository.findById(request.trackId())
                .orElseThrow();
        UserAssignment assignment = new UserAssignment();

        assignment.setId(UUID.randomUUID());
        assignment.setUserId(request.userId());
        assignment.setTrackId(request.trackId());
        assignment.setAssignedAt(Instant.now());
        assignment.setDueDate(request.dueDate());
        assignment.setStatus("ASSIGNED");
        assignment.setContentVersionAtAssignment(track.getVersion());

        return repository.save(assignment);
    }

}
