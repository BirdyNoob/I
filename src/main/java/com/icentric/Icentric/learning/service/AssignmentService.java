package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AssignmentService {

    private final UserAssignmentRepository repository;

    public AssignmentService(
            UserAssignmentRepository repository
    ) {
        this.repository = repository;
    }

    public UserAssignment assignTrack(CreateAssignmentRequest request) {
        UserAssignment assignment = new UserAssignment();

        assignment.setId(UUID.randomUUID());
        assignment.setUserId(request.userId());
        assignment.setTrackId(request.trackId());
        assignment.setAssignedAt(Instant.now());
        assignment.setDueDate(request.dueDate());
        assignment.setStatus("ASSIGNED");
        assignment.setContentVersionAtAssignment(1);

        return repository.save(assignment);
    }

}
