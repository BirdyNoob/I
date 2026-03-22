package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.audit.service.AuditService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AssignmentService {

    private final UserAssignmentRepository repository;
    private final TrackRepository trackRepository;
    private final AuditService auditService;

    public AssignmentService(
            UserAssignmentRepository repository, TrackRepository trackRepository, AuditService auditService
    ) {
        this.repository = repository;
        this.trackRepository = trackRepository;
        this.auditService = auditService;
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

        UserAssignment saved = repository.save(assignment);

        Object userIdRaw = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getDetails() : null;
        UUID adminUserId = userIdRaw != null ? (userIdRaw instanceof String ? UUID.fromString((String) userIdRaw) : UUID.fromString(userIdRaw.toString())) : null;
        if (adminUserId != null) {
            auditService.log(adminUserId, "ASSIGN_TRACK", "ASSIGNMENT", saved.getId().toString(), "Track assigned to user " + request.userId());
        }

        return saved;
    }

}
