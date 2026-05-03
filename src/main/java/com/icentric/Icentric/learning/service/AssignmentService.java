package com.icentric.Icentric.learning.service;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.identity.service.TenantAccessGuard;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.AdminAssignmentSearchResponse;
import com.icentric.Icentric.learning.dto.BulkAssignmentRequest;
import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final TenantSchemaService tenantSchemaService;
    private final AuditMetadataService auditMetadataService;
    private final TenantAccessGuard tenantAccessGuard;
    private final EmailService emailService;
    private final String publicBaseUrl;

    public AssignmentService(
            UserAssignmentRepository repository,
            TrackRepository trackRepository,
            AuditService auditService,
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            LessonRepository lessonRepository,
            LessonProgressRepository lessonProgressRepository,
            TenantSchemaService tenantSchemaService,
            AuditMetadataService auditMetadataService,
            TenantAccessGuard tenantAccessGuard,
            EmailService emailService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.repository = repository;
        this.trackRepository = trackRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.lessonRepository = lessonRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.auditMetadataService = auditMetadataService;
        this.tenantAccessGuard = tenantAccessGuard;
        this.emailService = emailService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public UserAssignment assignTrack(CreateAssignmentRequest request) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        UUID adminUserId = currentActorUserId();
        tenantAccessGuard.assertUserBelongsToCurrentTenant(request.userId());

        if (repository.findByUserIdAndTrackId(request.userId(), request.trackId()).isPresent()) {
            if (adminUserId != null) {
                auditService.log(
                        adminUserId,
                        AuditAction.ASSIGN_TRACK_SKIPPED,
                        "ASSIGNMENT",
                        request.trackId().toString(),
                        auditMetadataService.describeUser(adminUserId)
                                + " assignment skipped for "
                                + auditMetadataService.describeUserInCurrentTenant(request.userId())
                                + " because "
                                + auditMetadataService.describeTrack(request.trackId())
                                + " is already assigned"
                );
            }
            throw new IllegalArgumentException("Track is already assigned to this user");
        }

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

        // Send email notification to the learner
        Tenant tenant = tenantAccessGuard.currentTenant();
        userRepository.findById(request.userId()).ifPresent(u ->
                sendTrackAssignedNotification(u, track.getTitle(), tenant.getCompanyName(), tenant.getSlug(), request.dueDate())
        );

        return saved;
    }

    @Transactional
    public Map<String, Object> bulkAssign(BulkAssignmentRequest request) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        UUID adminUserId = currentActorUserId();

        List<User> users;
        Tenant tenant = tenantAccessGuard.currentTenant();

        if (request.userIds() != null && !request.userIds().isEmpty()) {
            List<UUID> requestedUserIds = request.userIds().stream().distinct().toList();
            tenantAccessGuard.assertUsersBelongToCurrentTenant(requestedUserIds);
            users = userRepository.findByIdIn(requestedUserIds);
        } else if (request.department() != null) {
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
                // Send email notification to the learner
                sendTrackAssignedNotification(user, track.getTitle(), tenant.getCompanyName(), tenant.getSlug(), request.dueDate());
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
    public Page<AdminAssignmentSearchResponse> searchAssignments(
            AssignmentStatus status,
            UUID trackId,
            UUID userId,
            Pageable pageable
    ) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        if (userId != null) {
            tenantAccessGuard.assertUserBelongsToCurrentTenant(userId);
        }

        Page<UserAssignment> assignments = repository.searchAssignments(
                status,
                trackId,
                userId,
                pageable
        );

        List<UserAssignment> content = assignments.getContent();
        if (content.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, assignments.getTotalElements());
        }

        Tenant tenant = tenantAccessGuard.currentTenant();
        Set<UUID> userIds = content.stream().map(UserAssignment::getUserId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> trackIds = content.stream().map(UserAssignment::getTrackId).collect(java.util.stream.Collectors.toSet());

        Map<UUID, User> usersById = userRepository.findByIdIn(userIds.stream().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));

        Map<UUID, TenantUser> membershipsByUserId = tenantUserRepository.findByTenantIdAndUserIdIn(tenant.getId(), userIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(TenantUser::getUserId, membership -> membership));

        Map<UUID, String> trackTitlesById = trackRepository.findAllById(trackIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(track -> track.getId(), track -> track.getTitle()));

        Map<UUID, Integer> totalLessonsByTrack = new HashMap<>();
        lessonRepository.countLessonsInTracks(trackIds)
                .forEach(row -> totalLessonsByTrack.put((UUID) row[0], ((Long) row[1]).intValue()));

        Map<String, Integer> completedLessonsByUserTrack = new HashMap<>();
        lessonProgressRepository.countCompletedLessonsByUserAndTrack(userIds, trackIds)
                .forEach(row -> completedLessonsByUserTrack.put(progressKey((UUID) row[0], (UUID) row[1]), ((Long) row[2]).intValue()));

        List<AdminAssignmentSearchResponse> responses = content.stream()
                .map(assignment -> toAdminAssignmentResponse(
                        assignment,
                        usersById.get(assignment.getUserId()),
                        membershipsByUserId.get(assignment.getUserId()),
                        trackTitlesById.get(assignment.getTrackId()),
                        totalLessonsByTrack.getOrDefault(assignment.getTrackId(), 0),
                        completedLessonsByUserTrack.getOrDefault(progressKey(assignment.getUserId(), assignment.getTrackId()), 0)
                ))
                .toList();

        return new PageImpl<>(responses, pageable, assignments.getTotalElements());
    }

    private AdminAssignmentSearchResponse toAdminAssignmentResponse(
            UserAssignment assignment,
            User user,
            TenantUser membership,
            String trackTitle,
            int totalLessons,
            int completedLessons
    ) {
        double completionPercent = totalLessons == 0
                ? 0
                : (completedLessons * 100.0) / totalLessons;

        return new AdminAssignmentSearchResponse(
                assignment.getId(),
                assignment.getUserId(),
                user != null ? user.getName() : "Unknown User",
                user != null ? user.getEmail() : null,
                membership != null ? membership.getDepartment() : null,
                assignment.getTrackId(),
                trackTitle != null ? trackTitle : "Unknown Track",
                assignment.getAssignedAt(),
                assignment.getDueDate(),
                assignment.getStatus(),
                assignment.getContentVersionAtAssignment(),
                assignment.getRequiresRetraining(),
                totalLessons,
                completedLessons,
                completionPercent
        );
    }

    private String progressKey(UUID userId, UUID trackId) {
        return userId + ":" + trackId;
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

    /**
     * Sends the AISafe_Email_Notification template to a learner when a track is assigned.
     * Failures are swallowed so they never roll back the assignment transaction.
     */
    public void sendTrackAssignedNotification(
            User user,
            String trackTitle,
            String tenantName,
            String tenantSlug,
            java.time.Instant dueDate
    ) {
        try {
            String displayName = user.getName() != null ? user.getName() : user.getEmail();
            String dueLine = dueDate != null
                    ? "<br><strong>Due by:</strong> " + dueDate.toString().substring(0, 10)
                    : "";
            String message = "You have been assigned a new learning track: <strong>" + trackTitle + "</strong>."
                    + dueLine
                    + "<br>Log in to your portal to start learning.";

            Map<String, Object> vars = new HashMap<>();
            vars.put("tenantName", tenantName);
            vars.put("notificationPill", "🎓\u00a0NEW TRACK ASSIGNED");
            vars.put("displayName", displayName);
            vars.put("title", "New track assigned: " + trackTitle);
            vars.put("message", message);
            vars.put("actionUrl", publicBaseUrl + "/login?tenant=" + tenantSlug);
            vars.put("actionText", "Start Learning →");

            emailService.sendTemplateEmail(
                    user.getEmail(),
                    "New Track Assigned: " + trackTitle,
                    "AISafe_Email_Notification",
                    vars
            );
        } catch (Exception ex) {
            // Log but never propagate — email failure must not roll back the assignment
            org.slf4j.LoggerFactory.getLogger(AssignmentService.class)
                    .warn("Failed to send track-assignment email to {}: {}", user.getEmail(), ex.getMessage());
        }
    }

}
