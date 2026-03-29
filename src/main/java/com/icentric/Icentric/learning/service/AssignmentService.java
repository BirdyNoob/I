package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.BulkAssignmentRequest;
import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AssignmentService {

    private final UserAssignmentRepository repository;
    private final TrackRepository trackRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantSchemaService tenantSchemaService;
    private final AuditMetadataService auditMetadataService;

    public AssignmentService(
            UserAssignmentRepository repository,
            TrackRepository trackRepository,
            AuditService auditService,
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            TenantSchemaService tenantSchemaService,
            AuditMetadataService auditMetadataService
    ) {
        this.repository = repository;
        this.trackRepository = trackRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.auditMetadataService = auditMetadataService;
    }

    @Transactional
    public UserAssignment assignTrack(CreateAssignmentRequest request) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        UUID adminUserId = currentActorUserId();

        var track = trackRepository.findById(request.trackId())
                .orElseThrow();
        UserAssignment assignment = new UserAssignment();

        assignment.setId(UUID.randomUUID());
        assignment.setUserId(request.userId());
        assignment.setTrackId(request.trackId());
        assignment.setAssignedAt(Instant.now());
        assignment.setDueDate(request.dueDate());
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setContentVersionAtAssignment(track.getVersion());

        UserAssignment saved = repository.save(assignment);

        if (adminUserId != null) {
            auditService.log(
                    adminUserId,
                    AuditAction.ASSIGN_TRACK,
                    "ASSIGNMENT",
                    saved.getId().toString(),
                    auditMetadataService.describeUser(adminUserId)
                            + " assigned "
                            + auditMetadataService.describeTrack(request.trackId())
                            + " to "
                            + auditMetadataService.describeUserInCurrentTenant(request.userId())
                            + (request.dueDate() != null ? " with due date " + request.dueDate() : "")
            );
        }

        return saved;
    }

    @Transactional
    public Map<String, Object> bulkAssign(BulkAssignmentRequest request) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        UUID adminUserId = currentActorUserId();

        List<User> users;

        if (request.userIds() != null && !request.userIds().isEmpty()) {
            users = userRepository.findByIdIn(request.userIds());
        } else if (request.department() != null) {
            // Resolve tenant from context
            String slug = TenantContext.getTenant();
            Tenant tenant = tenantRepository.findBySlug(slug)
                    .orElseThrow(() -> new IllegalStateException("Tenant not found: " + slug));

            // Find memberships by department within this tenant
            List<TenantUser> memberships = tenantUserRepository.findByTenantId(tenant.getId())
                    .stream()
                    .filter(m -> request.department().equals(m.getDepartment()))
                    .toList();

            List<UUID> userIds = memberships.stream().map(TenantUser::getUserId).toList();
            users = userRepository.findByIdIn(userIds);
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
                boolean exists = repository
                    .findByUserIdAndTrackId(user.getId(), request.trackId())
                    .isPresent();

                if (exists) {
                    if (adminUserId != null) {
                        auditService.log(
                                adminUserId,
                                AuditAction.ASSIGN_TRACK_SKIPPED,
                                "ASSIGNMENT",
                                request.trackId().toString(),
                                auditMetadataService.describeUser(adminUserId)
                                        + " bulk assignment skipped for "
                                        + auditMetadataService.describeUserInCurrentTenant(user.getId())
                                        + " because "
                                        + auditMetadataService.describeTrack(request.trackId())
                                        + " is already assigned"
                        );
                    }
                    skipped++;
                    continue;
                }

                UserAssignment assignment = new UserAssignment();
                assignment.setId(UUID.randomUUID());
                assignment.setUserId(user.getId());
                assignment.setTrackId(request.trackId());
                assignment.setAssignedAt(Instant.now());
                assignment.setDueDate(request.dueDate());
                assignment.setStatus(AssignmentStatus.ASSIGNED);
                assignment.setContentVersionAtAssignment(track.getVersion());
                assignment.setRequiresRetraining(false);

                UserAssignment saved = repository.save(assignment);
                if (adminUserId != null) {
                    auditService.log(
                            adminUserId,
                            AuditAction.ASSIGN_TRACK,
                            "ASSIGNMENT",
                            saved.getId().toString(),
                            auditMetadataService.describeUser(adminUserId)
                                    + " bulk assigned "
                                    + auditMetadataService.describeTrack(request.trackId())
                                    + " to "
                                    + auditMetadataService.describeUserInCurrentTenant(user.getId())
                                    + (request.dueDate() != null ? " with due date " + request.dueDate() : "")
                    );
                }
                success++;
            } catch (Exception e) {
                if (adminUserId != null) {
                    auditService.log(
                            adminUserId,
                            AuditAction.ASSIGN_TRACK_FAILED,
                            "ASSIGNMENT",
                            request.trackId().toString(),
                            auditMetadataService.describeUser(adminUserId)
                                    + " bulk assignment failed for "
                                    + auditMetadataService.describeUserInCurrentTenant(user.getId())
                                    + " on "
                                    + auditMetadataService.describeTrack(request.trackId())
                                    + ": " + e.getMessage()
                    );
                }
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

    @Transactional(readOnly = true)
    public Page<UserAssignment> searchAssignments(
            AssignmentStatus status,
            UUID trackId,
            UUID userId,
            Pageable pageable
    ) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        return repository.searchAssignments(
                status,
                trackId,
                userId,
                pageable
        );
    }

    private UUID currentActorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            return null;
        }
        return userIdRaw instanceof String
                ? UUID.fromString((String) userIdRaw)
                : UUID.fromString(userIdRaw.toString());
    }
}
