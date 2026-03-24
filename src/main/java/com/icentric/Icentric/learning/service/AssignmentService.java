package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.dto.BulkAssignmentRequest;
import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.audit.service.AuditService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssignmentService {

    private final UserAssignmentRepository repository;
    private final TrackRepository trackRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public AssignmentService(
            UserAssignmentRepository repository, TrackRepository trackRepository, AuditService auditService, UserRepository userRepository
    ) {
        this.repository = repository;
        this.trackRepository = trackRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
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
    public Map<String, Object> bulkAssign(BulkAssignmentRequest request) {

        List<User> users;

        // 🔥 Resolve users
        if (request.userIds() != null && !request.userIds().isEmpty()) {

            users = userRepository.findByIdIn(request.userIds());

        } else if (request.department() != null) {

            users = userRepository.findByDepartment(request.department());

        } else {
            throw new IllegalArgumentException("Provide userIds or department");
        }

        int total = users.size();
        int success = 0;
        int skipped = 0;

        List<String> errors = new ArrayList<>();
        var track = trackRepository.findById(request.trackId())
            .orElseThrow();

        for (User user : users) {

            try {

                // 🔥 avoid duplicate assignment
            boolean exists = repository
                .findByUserIdAndTrackId(user.getId(), request.trackId())
                .isPresent();

                if (exists) {
                    skipped++;
                    continue;
                }

                UserAssignment assignment = new UserAssignment();

                assignment.setId(UUID.randomUUID());
                assignment.setUserId(user.getId());
                assignment.setTrackId(request.trackId());
                assignment.setAssignedAt(Instant.now());
                assignment.setDueDate(request.dueDate());
                assignment.setStatus("ASSIGNED");

                assignment.setContentVersionAtAssignment(track.getVersion());
                assignment.setRequiresRetraining(false);

                repository.save(assignment);

                success++;

            } catch (Exception e) {
                errors.add("User " + user.getId() + ": " + e.getMessage());
            }
        }

        return Map.of(
                "total", total,
                "success", success,
                "skipped", skipped,
                "errors", errors
        );
    }

}
