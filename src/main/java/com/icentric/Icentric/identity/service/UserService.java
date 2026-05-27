package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.dto.BulkUploadResponse;
import com.icentric.Icentric.identity.dto.BulkUploadValidateResponse;
import com.icentric.Icentric.identity.dto.CsvRowValidationResult;
import com.icentric.Icentric.identity.dto.BulkUploadConfirmRequest;
import com.icentric.Icentric.identity.dto.BulkUploadRowDto;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.dto.UserResponse;
import com.icentric.Icentric.identity.dto.UserDetailResponse;
import com.icentric.Icentric.identity.dto.UserDetailResponse.AssignedTrackDetail;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.exception.UserNotFoundException;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.service.PlaywrightPdfService;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.icentric.Icentric.common.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * User management service.
 * <p>
 * Under the Global User Registry model, a "user within a tenant" is the
 * combination of a global {@link User} and a {@link TenantUser} mapping.
 * This service creates both records and joins them when returning data.
 */
@Service
public class UserService {
    private static final long MAX_BULK_UPLOAD_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_BULK_ROLES = Set.of("LEARNER", "ADMIN", "SUPER_ADMIN");
    private static final String BULK_UPLOAD_TEMPLATE_HEADER = "name,email,role,department";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bulkUploadDefaultPassword;
    private final AuditService auditService;
    private final AuditMetadataService auditMetadataService;
    private final TenantAccessGuard tenantAccessGuard;
    private final com.icentric.Icentric.common.mail.EmailService emailService;
    private final String publicBaseUrl;
    private final TrackRepository trackRepository;
    private final UserAssignmentRepository userAssignmentRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final IssuedCertificateRepository issuedCertificateRepository;
    private final LessonRepository lessonRepository;
    private final TenantSchemaService tenantSchemaService;
    private final PlaywrightPdfService playwrightPdfService;

    public UserService(
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bulk-upload.default-password:ChangeMe@123}") String bulkUploadDefaultPassword,
            AuditService auditService,
            AuditMetadataService auditMetadataService,
            TenantAccessGuard tenantAccessGuard,
            com.icentric.Icentric.common.mail.EmailService emailService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
            TrackRepository trackRepository,
            UserAssignmentRepository userAssignmentRepository,
            LessonProgressRepository lessonProgressRepository,
            IssuedCertificateRepository issuedCertificateRepository,
            LessonRepository lessonRepository,
            TenantSchemaService tenantSchemaService,
            PlaywrightPdfService playwrightPdfService
    ) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.bulkUploadDefaultPassword = bulkUploadDefaultPassword;
        this.auditService = auditService;
        this.auditMetadataService = auditMetadataService;
        this.tenantAccessGuard = tenantAccessGuard;
        this.emailService = emailService;
        this.publicBaseUrl = publicBaseUrl;
        this.trackRepository = trackRepository;
        this.userAssignmentRepository = userAssignmentRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.issuedCertificateRepository = issuedCertificateRepository;
        this.lessonRepository = lessonRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.playwrightPdfService = playwrightPdfService;
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Tenant tenant = tenantAccessGuard.currentTenant();

        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        if (actorMembership != null) {
            if ("ADMIN".equals(actorMembership.getRole())) {
                if ("SUPER_ADMIN".equalsIgnoreCase(request.role())) {
                    throw new org.springframework.security.access.AccessDeniedException("Managers are not authorized to create SUPER_ADMIN users");
                }
            }
        }

        // Upsert global user
        User user = userRepository.findByEmail(request.email())
                .orElseGet(() -> {
                    User u = new User();
                    u.setId(UUID.randomUUID());
                    u.setName(normalizeName(request.name()));
                    u.setEmail(request.email());
                    u.setLocation(normalizeLocation(request.location()));
                    u.setPasswordHash(passwordEncoder.encode(request.password()));
                    u.setAuthProvider("LOCAL");
                    u.setIsActive(true);
                    u.setCreatedAt(Instant.now());
                    return userRepository.save(u);
                });

        // Create tenant mapping
        TenantUser mapping = new TenantUser(user.getId(), tenant.getId(), request.role());
        mapping.setDepartment(request.department());
        if (actorId != null) {
            mapping.setCreatedBy(actorId);
        }
        tenantUserRepository.save(mapping);

        // Auto-assign department tracks if the toggle is enabled
        if (Boolean.TRUE.equals(request.autoAssignTracks()) && request.department() != null) {
            autoAssignDepartmentTracks(user, tenant, request.department());
        }

        logAdminAction(
                AuditAction.CREATE_USER,
                "USER",
                user.getId().toString(),
                actor -> actor + " created " + auditMetadataService.describeUserInCurrentTenant(user.getId())
                        + " in " + auditMetadataService.currentTenantLabel()
        );

        String userName = user.getName() != null ? user.getName() : user.getEmail();
        if ("LEARNER".equalsIgnoreCase(request.role())) {
            java.util.Map<String, Object> variables = java.util.Map.of(
                    "userName", userName,
                    "tenantName", tenant.getCompanyName(),
                    "portalUrl", publicBaseUrl + "/login?tenant=" + tenant.getSlug(),
                    "userEmail", request.email(),
                    "password", request.password()
            );
            emailService.sendTemplateEmail(
                    user.getEmail(),
                    "Welcome to AISafe - Learner Account Created",
                    "AISafe_Email_Learner_Welcome",
                    variables
            );
        } else if ("ADMIN".equalsIgnoreCase(request.role())) {
            java.util.Map<String, Object> variables = java.util.Map.of(
                    "userName", userName,
                    "tenantName", tenant.getCompanyName(),
                    "managerEmail", request.email(),
                    "password", request.password(),
                    "portalUrl", tenant.getSlug() + ".icentric.com",
                    "loginUrl", publicBaseUrl + "/login?tenant=" + tenant.getSlug()
            );
            emailService.sendTemplateEmail(
                    user.getEmail(),
                    "Welcome to Icentric — Your Manager account is ready",
                    "Icentric_Email_Manager_Welcome",
                    variables
            );
        } else {
            java.util.Map<String, Object> variables = java.util.Map.of(
                    "userName", userName,
                    "tenantName", tenant.getCompanyName(),
                    "adminEmail", request.email(),
                    "adminPassword", request.password(),
                    "portalUrl", tenant.getSlug() + ".icentric.com",
                    "planName", "Enterprise",
                    "seatLimit", 500,
                    "setupUrl", publicBaseUrl + "/setup?tenant=" + tenant.getSlug(),
                    "loginUrl", publicBaseUrl + "/login?tenant=" + tenant.getSlug()
            );
            emailService.sendTemplateEmail(
                    user.getEmail(),
                    "Welcome to AISafe - Administrator Account Created",
                    "AISafe_Email_TenantAdmin_Welcome",
                    variables
            );
        }

        return toResponse(user, mapping);
    }

    // ── READ (paginated, filtered) ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(
            Department department,
            String role,
            Boolean isActive,
            Pageable pageable
    ) {
        Tenant tenant = tenantAccessGuard.currentTenant();
        UUID actorId = currentActorUserId();
        UUID createdByFilter = null;
        if (actorId != null) {
            TenantUser actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
            if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
                createdByFilter = actorId;
            }
        }

        return userRepository.findTenantUsers(
                tenant.getId(),
                createdByFilter,
                department,
                role,
                isActive,
                pageable
        );
    }

    // ── USER DETAIL ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        Tenant tenant = tenantAccessGuard.currentTenant();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        TenantUser membership = tenantUserRepository
                .findByUserIdAndTenantId(userId, tenant.getId())
                .orElseThrow(() -> new UserNotFoundException(userId));

        UUID actorId = currentActorUserId();
        if (actorId != null) {
            TenantUser actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
            if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
                if (!actorId.equals(membership.getCreatedBy())) {
                    throw new org.springframework.security.access.AccessDeniedException("Access denied: You are not authorized to view this user");
                }
            }
        }

        // All assignments for this user
        List<UserAssignment> assignments = userAssignmentRepository.findByUserId(userId);
        List<UUID> trackIds = assignments.stream().map(UserAssignment::getTrackId).toList();

        // Batch-load lesson totals per track
        Map<UUID, Long> lessonTotals = new HashMap<>();
        if (!trackIds.isEmpty()) {
            for (Object[] row : lessonRepository.countLessonsInTracks(trackIds)) {
                lessonTotals.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }

        // Batch-load completed lessons per track for this user
        Map<UUID, Long> completedPerTrack = new HashMap<>();
        if (!trackIds.isEmpty()) {
            for (Object[] row : lessonProgressRepository.countCompletedLessonsByTrack(userId, trackIds)) {
                completedPerTrack.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }

        // Certificates earned
        long certCount = issuedCertificateRepository.findByUserId(userId).size();

        Instant now = Instant.now();

        List<AssignedTrackDetail> assignedTracks = new ArrayList<>();
        long progressDone = 0;
        long progressTotal = 0;

        for (UserAssignment a : assignments) {
            Track track = trackRepository.findById(a.getTrackId()).orElse(null);
            if (track == null) continue;

            long done = completedPerTrack.getOrDefault(a.getTrackId(), 0L);
            long total = lessonTotals.getOrDefault(a.getTrackId(), 0L);
            boolean overdue = a.getStatus() != AssignmentStatus.COMPLETED
                    && a.getDueDate() != null
                    && a.getDueDate().isBefore(now);

            // Completed-at: derive from the last completed lesson timestamp if status is COMPLETED
            Instant completedAt = null;
            if (a.getStatus() == AssignmentStatus.COMPLETED) {
                List<Instant> timestamps = lessonProgressRepository.findCompletedTimestampsByUserId(userId);
                completedAt = timestamps.isEmpty() ? null : timestamps.get(0);
            }

            progressDone += done;
            progressTotal += total;

            assignedTracks.add(new AssignedTrackDetail(
                    track.getId(),
                    track.getTitle(),
                    a.getStatus().name(),
                    completedAt,
                    a.getDueDate(),
                    done,
                    total,
                    overdue
            ));
        }

        String lastActive = formatLastActive(user.getLastLoginAt());

        return new UserDetailResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                membership.getDepartment(),
                membership.getRole(),
                user.getIsActive(),
                user.getCreatedAt(),
                lastActive,
                user.getLocation(),
                progressDone,
                progressTotal,
                certCount,
                assignedTracks
        );
    }

    private String formatLastActive(Instant lastLoginAt) {
        if (lastLoginAt == null) return "Never";
        long seconds = Instant.now().getEpochSecond() - lastLoginAt.getEpochSecond();
        if (seconds < 60) return "Just now";
        if (seconds < 3600) return (seconds / 60) + " minutes ago";
        if (seconds < 86400) return (seconds / 3600) + " hours ago";
        long days = seconds / 86400;
        if (days == 1) return "1 day ago";
        if (days < 30) return days + " days ago";
        if (days < 365) return (days / 30) + " months ago";
        return (days / 365) + " years ago";
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        Tenant tenant = tenantAccessGuard.currentTenant();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        TenantUser mapping = tenantUserRepository
                .findByUserIdAndTenantId(userId, tenant.getId())
                .orElseThrow(() -> new UserNotFoundException(userId));

        UUID actorId = currentActorUserId();
        TenantUser actorMembership = null;
        if (actorId != null) {
            actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
        }

        if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
            if (!actorId.equals(mapping.getCreatedBy())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: You are not authorized to modify this user");
            }
            if (request.role() != null && "SUPER_ADMIN".equalsIgnoreCase(request.role())) {
                throw new org.springframework.security.access.AccessDeniedException("Managers are not authorized to promote users to SUPER_ADMIN");
            }
        }

        // Update global fields
        if (request.name() != null) {
            user.setName(normalizeName(request.name()));
        }
        if (request.location() != null) {
            user.setLocation(normalizeLocation(request.location()));
        }

        // Update tenant-level fields
        if (request.role() != null) {
            mapping.setRole(request.role());
        }
        if (request.department() != null) {
            mapping.setDepartment(request.department());
        }
        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }

        userRepository.save(user);
        tenantUserRepository.save(mapping);

        logAdminAction(
                AuditAction.UPDATE_USER,
                "USER",
                user.getId().toString(),
                actor -> actor + " updated " + auditMetadataService.describeUserInCurrentTenant(user.getId())
        );

        return toResponse(user, mapping);
    }

    // ── DEACTIVATE ───────────────────────────────────────────────────────────

    @Transactional
    public void deactivateUser(UUID userId) {
        tenantAccessGuard.assertUserBelongsToCurrentTenant(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Tenant tenant = tenantAccessGuard.currentTenant();
        UUID actorId = currentActorUserId();
        if (actorId != null) {
            TenantUser actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
            if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
                TenantUser mapping = tenantUserRepository.findByUserIdAndTenantId(userId, tenant.getId())
                        .orElseThrow(() -> new UserNotFoundException(userId));
                if (!actorId.equals(mapping.getCreatedBy())) {
                    throw new org.springframework.security.access.AccessDeniedException("Access denied: You are not authorized to deactivate this user");
                }
            }
        }

        user.setIsActive(false);
        userRepository.save(user);

        logAdminAction(
                AuditAction.DEACTIVATE_USER,
                "USER",
                user.getId().toString(),
                actor -> actor + " deactivated " + auditMetadataService.describeUserInCurrentTenant(user.getId())
        );
    }

    // ── SEARCH ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(
            String name,
            String email,
            Department department,
            String role,
            Boolean isActive,
            Pageable pageable
    ) {
        Tenant tenant = tenantAccessGuard.currentTenant();

        UUID actorId = currentActorUserId();
        UUID createdByFilter = null;
        if (actorId != null) {
            TenantUser actorMembership = tenantUserRepository.findByUserIdAndTenantId(actorId, tenant.getId()).orElse(null);
            if (actorMembership != null && "ADMIN".equals(actorMembership.getRole())) {
                createdByFilter = actorId;
            }
        }

        String normalizedName = normalizeSearchText(name);
        String normalizedEmail = normalizeSearchEmail(email);

        return userRepository.searchTenantUsers(
                tenant.getId(),
                createdByFilter,
                department,
                role,
                isActive,
                normalizedName,
                normalizedEmail,
                pageable
        );
    }

    // ── BULK UPLOAD ──────────────────────────────────────────────────────────

    public String getBulkUploadTemplateCsv() {
        return BULK_UPLOAD_TEMPLATE_HEADER + "\n" +
                "Jane Doe,jane.doe@example.com,LEARNER,Engineering\n" +
                "John Smith,john.smith@example.com,ADMIN,Sales\n";
    }

    public byte[] getBulkUploadInstructionsPdf() {
        StringBuilder deptsHtml = new StringBuilder();
        for (Department dept : Department.values()) {
            deptsHtml.append("            <div class=\"chip\">")
                    .append(dept.getDisplayName())
                    .append("</div>\n");
        }

        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Bulk Upload User Guide</title>\n" +
                "    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">\n" +
                "    <style>\n" +
                "        @page {\n" +
                "            size: A4 portrait;\n" +
                "            margin: 0;\n" +
                "        }\n" +
                "        body {\n" +
                "            font-family: 'Inter', sans-serif;\n" +
                "            color: #1e293b;\n" +
                "            background-color: #f8fafc;\n" +
                "            margin: 0;\n" +
                "            padding: 40px;\n" +
                "            box-sizing: border-box;\n" +
                "            -webkit-print-color-adjust: exact;\n" +
                "        }\n" +
                "        .container {\n" +
                "            background-color: #ffffff;\n" +
                "            border: 1px solid #e2e8f0;\n" +
                "            border-radius: 12px;\n" +
                "            padding: 35px;\n" +
                "            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);\n" +
                "        }\n" +
                "        .header {\n" +
                "            border-bottom: 2px solid #e2e8f0;\n" +
                "            padding-bottom: 20px;\n" +
                "            margin-bottom: 25px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: space-between;\n" +
                "        }\n" +
                "        .logo {\n" +
                "            font-size: 24px;\n" +
                "            font-weight: 800;\n" +
                "            color: #1e1b4b;\n" +
                "            letter-spacing: -0.025em;\n" +
                "        }\n" +
                "        .logo span {\n" +
                "            color: #4f46e5;\n" +
                "        }\n" +
                "        .badge {\n" +
                "            background-color: #e0e7ff;\n" +
                "            color: #4338ca;\n" +
                "            padding: 4px 12px;\n" +
                "            border-radius: 9999px;\n" +
                "            font-size: 11px;\n" +
                "            font-weight: 600;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .title {\n" +
                "            font-size: 22px;\n" +
                "            font-weight: 700;\n" +
                "            color: #0f172a;\n" +
                "            margin-top: 15px;\n" +
                "            margin-bottom: 5px;\n" +
                "        }\n" +
                "        .subtitle {\n" +
                "            font-size: 13px;\n" +
                "            color: #64748b;\n" +
                "            margin-bottom: 25px;\n" +
                "        }\n" +
                "        .alert-box {\n" +
                "            background-color: #fff1f2;\n" +
                "            border: 1px solid #fecdd3;\n" +
                "            border-left: 5px solid #f43f5e;\n" +
                "            border-radius: 8px;\n" +
                "            padding: 18px 20px;\n" +
                "            margin-bottom: 30px;\n" +
                "        }\n" +
                "        .alert-title {\n" +
                "            color: #9f1239;\n" +
                "            font-weight: 700;\n" +
                "            font-size: 14px;\n" +
                "            margin-bottom: 6px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            gap: 8px;\n" +
                "        }\n" +
                "        .alert-content {\n" +
                "            color: #be123c;\n" +
                "            font-size: 12px;\n" +
                "            line-height: 1.6;\n" +
                "            font-weight: 500;\n" +
                "        }\n" +
                "        .section-title {\n" +
                "            font-size: 15px;\n" +
                "            font-weight: 600;\n" +
                "            color: #1e1b4b;\n" +
                "            margin-top: 25px;\n" +
                "            margin-bottom: 12px;\n" +
                "            text-transform: uppercase;\n" +
                "            letter-spacing: 0.05em;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            gap: 6px;\n" +
                "        }\n" +
                "        table {\n" +
                "            width: 100%;\n" +
                "            border-collapse: collapse;\n" +
                "            margin-bottom: 25px;\n" +
                "        }\n" +
                "        th {\n" +
                "            background-color: #f8fafc;\n" +
                "            color: #475569;\n" +
                "            font-weight: 600;\n" +
                "            font-size: 11px;\n" +
                "            text-transform: uppercase;\n" +
                "            text-align: left;\n" +
                "            padding: 10px 14px;\n" +
                "            border-bottom: 2px solid #e2e8f0;\n" +
                "        }\n" +
                "        td {\n" +
                "            padding: 12px 14px;\n" +
                "            border-bottom: 1px solid #f1f5f9;\n" +
                "            font-size: 12px;\n" +
                "            line-height: 1.5;\n" +
                "            vertical-align: top;\n" +
                "        }\n" +
                "        .required-tag {\n" +
                "            background-color: #fee2e2;\n" +
                "            color: #ef4444;\n" +
                "            padding: 2px 6px;\n" +
                "            border-radius: 4px;\n" +
                "            font-size: 9px;\n" +
                "            font-weight: 600;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .optional-tag {\n" +
                "            background-color: #f1f5f9;\n" +
                "            color: #64748b;\n" +
                "            padding: 2px 6px;\n" +
                "            border-radius: 4px;\n" +
                "            font-size: 9px;\n" +
                "            font-weight: 600;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .grid-container {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: repeat(3, 1fr);\n" +
                "            gap: 8px;\n" +
                "            margin-bottom: 25px;\n" +
                "        }\n" +
                "        .chip {\n" +
                "            background-color: #f8fafc;\n" +
                "            border: 1px solid #e2e8f0;\n" +
                "            border-radius: 6px;\n" +
                "            padding: 8px 10px;\n" +
                "            font-size: 11px;\n" +
                "            font-weight: 500;\n" +
                "            color: #334155;\n" +
                "            text-align: center;\n" +
                "            white-space: nowrap;\n" +
                "            overflow: hidden;\n" +
                "            text-overflow: ellipsis;\n" +
                "        }\n" +
                "        .role-list {\n" +
                "            display: flex;\n" +
                "            gap: 10px;\n" +
                "            margin-bottom: 25px;\n" +
                "        }\n" +
                "        .role-card {\n" +
                "            background-color: #eff6ff;\n" +
                "            border: 1px solid #bfdbfe;\n" +
                "            border-radius: 6px;\n" +
                "            padding: 10px 15px;\n" +
                "            font-size: 12px;\n" +
                "            font-weight: 600;\n" +
                "            color: #1d4ed8;\n" +
                "            flex: 1;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .footer {\n" +
                "            margin-top: 30px;\n" +
                "            border-top: 1px solid #e2e8f0;\n" +
                "            padding-top: 15px;\n" +
                "            text-align: center;\n" +
                "            font-size: 11px;\n" +
                "            color: #94a3b8;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <div class=\"logo\">I<span>CENTRIC</span></div>\n" +
                "            <div class=\"badge\">Reference Guide</div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"title\">User Bulk Onboarding Instructions</div>\n" +
                "        <div class=\"subtitle\">Quick reference guide detailing accepted format guidelines, fields, roles, and department names.</div>\n" +
                "        \n" +
                "        <div class=\"alert-box\">\n" +
                "            <div class=\"alert-title\">\n" +
                "                <svg width=\"16\" height=\"16\" viewBox=\"0 0 20 20\" fill=\"currentColor\" style=\"display:inline-block;vertical-align:middle;margin-right:4px;\">\n" +
                "                    <path fill-rule=\"evenodd\" d=\"M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z\" clip-rule=\"evenodd\" />\n" +
                "                </svg>\n" +
                "                CRITICAL WARNING: DO NOT UPLOAD THIS GUIDE\n" +
                "            </div>\n" +
                "            <div class=\"alert-content\">\n" +
                "                <strong>IMPORTANT:</strong> This PDF is strictly for informational and reference purposes. Please <strong>do NOT</strong> upload this PDF document, and do <strong>NOT</strong> copy these instructions or headers into your CSV file. Your final uploaded CSV file must strictly contain only clean rows of actual users to be registered in the system.\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"section-title\">1. CSV Field Requirements</div>\n" +
                "        <table>\n" +
                "            <thead>\n" +
                "                <tr>\n" +
                "                    <th style=\"width: 15%\">Field Name</th>\n" +
                "                    <th style=\"width: 15%\">Requirement</th>\n" +
                "                    <th style=\"width: 30%\">Accepted Format / Allowed Values</th>\n" +
                "                    <th style=\"width: 40%\">Notes & Constraints</th>\n" +
                "                </tr>\n" +
                "            </thead>\n" +
                "            <tbody>\n" +
                "                <tr>\n" +
                "                    <td><strong>name</strong></td>\n" +
                "                    <td><span class=\"required-tag\">Required</span></td>\n" +
                "                    <td>Any valid full name string.</td>\n" +
                "                    <td>Minimum 2 characters, leading/trailing whitespace is auto-trimmed. Example: <em>Jane Doe</em></td>\n" +
                "                </tr>\n" +
                "                <tr>\n" +
                "                    <td><strong>email</strong></td>\n" +
                "                    <td><span class=\"required-tag\">Required</span></td>\n" +
                "                    <td>Standard email address layout.</td>\n" +
                "                    <td>Must be globally unique. Case-insensitive and automatically normalized. Example: <em>jane.doe@example.com</em></td>\n" +
                "                </tr>\n" +
                "                <tr>\n" +
                "                    <td><strong>role</strong></td>\n" +
                "                    <td><span class=\"required-tag\">Required</span></td>\n" +
                "                    <td><code>ADMIN</code> or <code>LEARNER</code></td>\n" +
                "                    <td>Case-insensitive. Standard managers can only onboard users within their allowed hierarchy.</td>\n" +
                "                </tr>\n" +
                "                <tr>\n" +
                "                    <td><strong>department</strong></td>\n" +
                "                    <td><span class=\"optional-tag\">Optional</span></td>\n" +
                "                    <td>Name of system department (see below).</td>\n" +
                "                    <td>Must match one of the exact system departments listed below (either standard name or display name). If empty, department is set to null.</td>\n" +
                "                </tr>\n" +
                "            </tbody>\n" +
                "        </table>\n" +
                "        \n" +
                "        <div class=\"section-title\">2. Allowed Roles</div>\n" +
                "        <div class=\"role-list\">\n" +
                "            <div class=\"role-card\">LEARNER</div>\n" +
                "            <div class=\"role-card\">ADMIN</div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"section-title\">3. Allowed System Departments</div>\n" +
                "        <div class=\"grid-container\">\n" +
                "            " + deptsHtml.toString() + "\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"footer\">\n" +
                "            &copy; 2026 Icentric Learning Platform. All rights reserved. Reference ID: UG-BULK-V1\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        return playwrightPdfService.render(html, false);
    }

    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect old password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logAdminAction(
                AuditAction.UPDATE_USER,
                "USER",
                user.getId().toString(),
                actor -> actor + " changed their password in " + auditMetadataService.currentTenantLabel()
        );
    }

    @Transactional
    public BulkUploadResponse bulkUploadUsers(MultipartFile file, Boolean autoAssignTracks) {
        Tenant tenant = tenantAccessGuard.currentTenant();
        validateBulkUploadFile(file);
        validateBulkUploadDefaults();

        int total = 0;
        int success = 0;
        int failed = 0;

        List<String> errors = new ArrayList<>();
        List<RowCandidate> candidates = new ArrayList<>();
        Set<String> seenEmailsInFile = new HashSet<>();

        try (
                Reader reader = createCsvReader(file);
                CSVParser csvParser = new CSVParser(reader,
                        CSVFormat.DEFAULT
                                .withFirstRecordAsHeader()
                                .withIgnoreEmptyLines()
                                .withIgnoreHeaderCase()
                                .withTrim())
        ) {
            validateRequiredHeaders(csvParser);

            for (CSVRecord record : csvParser) {
                long rowNumber = record.getRecordNumber() + 1;

                if (isBlankRow(record)) {
                    continue;
                }

                total++;

                try {
                    String emailVal = normalizeEmail(record.get("email"));
                    String nameVal = normalizeName(record.get("name"));
                    String roleVal = normalizeRole(record.get("role"));
                    Department departmentVal = normalizeDepartment(record.get("department"));

                    validateCandidate(nameVal, emailVal, roleVal);

                    UUID actId = currentActorUserId();
                    if (actId != null) {
                        TenantUser actMem = tenantUserRepository.findByUserIdAndTenantId(actId, tenant.getId()).orElse(null);
                        if (actMem != null && "ADMIN".equals(actMem.getRole())) {
                            if ("SUPER_ADMIN".equalsIgnoreCase(roleVal)) {
                                throw new IllegalArgumentException("Managers are not authorized to upload SUPER_ADMIN users");
                            }
                        }
                    }

                    if (!seenEmailsInFile.add(emailVal)) {
                        throw new IllegalArgumentException("Duplicate email in file");
                    }

                    candidates.add(new RowCandidate(rowNumber, nameVal, emailVal, roleVal, departmentVal));

                } catch (IllegalArgumentException e) {
                    failed++;
                    errors.add("Row " + rowNumber + ": " + e.getMessage());
                }
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("CSV processing failed", e);
        }

        if (candidates.isEmpty()) {
            return new BulkUploadResponse(total, success, failed, errors);
        }

        // Check which emails already exist globally
        Set<String> candidateEmails = new LinkedHashSet<>();
        for (RowCandidate candidate : candidates) {
            candidateEmails.add(candidate.email());
        }

        Map<String, User> existingUsers = new HashMap<>();
        for (User existingUser : userRepository.findAllByEmailLowerIn(candidateEmails)) {
            existingUsers.put(existingUser.getEmail().toLowerCase(Locale.ROOT), existingUser);
        }

        // Check which emails already have a mapping in THIS tenant
        Set<UUID> candidateUserIds = existingUsers.values().stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        Set<UUID> existingMappedUserIds = new HashSet<>(tenantUserRepository.findUserIdsByTenantIdAndUserIdIn(
                tenant.getId(),
                candidateUserIds
        ));

        Instant createdAt = Instant.now();
        String passwordHash = passwordEncoder.encode(bulkUploadDefaultPassword);

        for (RowCandidate candidate : candidates) {
            User user = existingUsers.get(candidate.email());

            if (user != null && existingMappedUserIds.contains(user.getId())) {
                // User already exists AND is already mapped to this tenant
                failed++;
                errors.add("Row " + candidate.rowNumber() + ": User already exists in this tenant");
                continue;
            }

            // Create global user if needed
            if (user == null) {
                user = new User();
                user.setId(UUID.randomUUID());
                user.setName(candidate.name());
                user.setEmail(candidate.email());
                user.setPasswordHash(passwordHash);
                user.setAuthProvider("LOCAL");
                user.setIsActive(true);
                user.setCreatedAt(createdAt);
                user = userRepository.save(user);
            }

            // Create tenant mapping
            TenantUser mapping = new TenantUser(user.getId(), tenant.getId(), candidate.role());
            mapping.setDepartment(candidate.department());
            UUID actId = currentActorUserId();
            if (actId != null) {
                mapping.setCreatedBy(actId);
            }
            tenantUserRepository.save(mapping);

            // Auto-assign department tracks if the toggle is enabled
            if (Boolean.TRUE.equals(autoAssignTracks) && candidate.department() != null) {
                autoAssignDepartmentTracks(user, tenant, candidate.department());
            }

            success++;
        }

        int totalRows = total;
        int successRows = success;
        int failedRows = failed;
        logAdminAction(
                AuditAction.BULK_UPLOAD_USERS,
                "USER",
                tenant.getId().toString(),
                actor -> actor + " processed bulk user upload in " + auditMetadataService.currentTenantLabel()
                        + ": total=" + totalRows + ", success=" + successRows + ", failed=" + failedRows
        );

        return new BulkUploadResponse(total, success, failed, errors);
    }

    @Transactional(readOnly = true)
    public BulkUploadValidateResponse validateBulkUpload(MultipartFile file) {
        validateBulkUploadFile(file);
        Tenant tenant = tenantAccessGuard.currentTenant();

        List<CsvRowValidationResult> rows = new ArrayList<>();
        int totalRows = 0;
        int validRowsCount = 0;
        int invalidRowsCount = 0;

        List<TempRowCandidate> candidates = new ArrayList<>();
        Set<String> seenEmailsInFile = new HashSet<>();

        try (
                Reader reader = createCsvReader(file);
                CSVParser csvParser = new CSVParser(reader,
                        CSVFormat.DEFAULT
                                .withFirstRecordAsHeader()
                                .withIgnoreEmptyLines()
                                .withIgnoreHeaderCase()
                                .withTrim())
        ) {
            validateRequiredHeaders(csvParser);

            for (CSVRecord record : csvParser) {
                long rowNumber = record.getRecordNumber() + 1;

                if (isBlankRow(record)) {
                    continue;
                }

                totalRows++;

                String emailRaw = record.isMapped("email") ? record.get("email") : null;
                String nameRaw = record.isMapped("name") ? record.get("name") : null;
                String roleRaw = record.isMapped("role") ? record.get("role") : null;
                String departmentRaw = record.isMapped("department") ? record.get("department") : null;

                String emailVal = normalizeEmail(emailRaw);
                String nameVal = normalizeName(nameRaw);
                String roleVal = normalizeRole(roleRaw);
                Department departmentVal = normalizeDepartment(departmentRaw);

                List<String> errors = new ArrayList<>();

                if (nameVal == null || nameVal.isBlank()) {
                    errors.add("Name is required");
                }
                if (emailVal == null || emailVal.isBlank()) {
                    errors.add("Email is required");
                } else if (!EMAIL_PATTERN.matcher(emailVal).matches()) {
                    errors.add("Invalid email format");
                }
                if (roleVal == null || roleVal.isBlank()) {
                    errors.add("Role is required");
                } else if (!ALLOWED_BULK_ROLES.contains(roleVal)) {
                    errors.add("Invalid role: must be LEARNER, ADMIN, or SUPER_ADMIN");
                }

                if (departmentRaw != null && !departmentRaw.trim().isEmpty() && departmentVal == null) {
                    errors.add("Invalid department: must match a valid system department");
                }

                if (roleVal != null && "SUPER_ADMIN".equalsIgnoreCase(roleVal)) {
                    UUID actId = currentActorUserId();
                    if (actId != null) {
                        TenantUser actMem = tenantUserRepository.findByUserIdAndTenantId(actId, tenant.getId()).orElse(null);
                        if (actMem != null && "ADMIN".equals(actMem.getRole())) {
                            errors.add("Managers are not authorized to upload SUPER_ADMIN users");
                        }
                    }
                }

                if (emailVal != null && !emailVal.isBlank()) {
                    if (!seenEmailsInFile.add(emailVal)) {
                        errors.add("Duplicate email in CSV file");
                    }
                }

                candidates.add(new TempRowCandidate(
                        rowNumber,
                        nameVal != null ? nameVal : (nameRaw != null ? nameRaw.trim() : ""),
                        emailVal != null ? emailVal : (emailRaw != null ? emailRaw.trim().toLowerCase(Locale.ROOT) : ""),
                        roleVal != null ? roleVal : (roleRaw != null ? roleRaw.trim().toUpperCase(Locale.ROOT) : ""),
                        departmentVal,
                        departmentRaw,
                        errors
                ));
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("CSV processing failed", e);
        }

        Set<String> uniqueEmails = candidates.stream()
                .map(TempRowCandidate::email)
                .filter(email -> email != null && !email.isBlank())
                .collect(Collectors.toSet());

        Map<String, User> existingUsers = new HashMap<>();
        if (!uniqueEmails.isEmpty()) {
            for (User existingUser : userRepository.findAllByEmailLowerIn(uniqueEmails)) {
                existingUsers.put(existingUser.getEmail().toLowerCase(Locale.ROOT), existingUser);
            }
        }

        Set<UUID> candidateUserIds = existingUsers.values().stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        Set<UUID> existingMappedUserIds = new HashSet<>();
        if (!candidateUserIds.isEmpty()) {
            existingMappedUserIds.addAll(tenantUserRepository.findUserIdsByTenantIdAndUserIdIn(
                    tenant.getId(),
                    candidateUserIds
            ));
        }

        for (TempRowCandidate candidate : candidates) {
            List<String> rowErrors = new ArrayList<>(candidate.errors());

            if (candidate.email() != null && !candidate.email().isBlank()) {
                User user = existingUsers.get(candidate.email());
                if (user != null && existingMappedUserIds.contains(user.getId())) {
                    rowErrors.add("User already exists in this tenant");
                }
            }

            boolean isValid = rowErrors.isEmpty();
            if (isValid) {
                validRowsCount++;
            } else {
                invalidRowsCount++;
            }

            rows.add(new CsvRowValidationResult(
                    candidate.rowNumber(),
                    candidate.name(),
                    candidate.email(),
                    candidate.role(),
                    candidate.departmentRaw() != null ? candidate.departmentRaw().trim() : "",
                    isValid,
                    rowErrors
            ));
        }

        return new BulkUploadValidateResponse(totalRows, validRowsCount, invalidRowsCount, rows);
    }

    @Transactional
    public BulkUploadResponse confirmBulkUpload(BulkUploadConfirmRequest request) {
        Tenant tenant = tenantAccessGuard.currentTenant();
        validateBulkUploadDefaults();

        if (request == null || request.users() == null) {
            throw new IllegalArgumentException("Request body and users list are required");
        }

        int total = request.users().size();
        int success = 0;
        int failed = 0;

        List<String> errors = new ArrayList<>();
        List<RowCandidate> candidates = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();

        long rowNum = 1;
        for (BulkUploadRowDto userDto : request.users()) {
            long currentRow = rowNum++;
            try {
                if (userDto == null) {
                    throw new IllegalArgumentException("User record is empty");
                }

                String emailVal = normalizeEmail(userDto.email());
                String nameVal = normalizeName(userDto.name());
                String roleVal = normalizeRole(userDto.role());
                Department departmentVal = normalizeDepartment(userDto.department());

                validateCandidate(nameVal, emailVal, roleVal);

                UUID actId = currentActorUserId();
                if (actId != null) {
                    TenantUser actMem = tenantUserRepository.findByUserIdAndTenantId(actId, tenant.getId()).orElse(null);
                    if (actMem != null && "ADMIN".equals(actMem.getRole())) {
                        if ("SUPER_ADMIN".equalsIgnoreCase(roleVal)) {
                            throw new IllegalArgumentException("Managers are not authorized to upload SUPER_ADMIN users");
                        }
                    }
                }

                if (userDto.department() != null && !userDto.department().trim().isEmpty() && departmentVal == null) {
                    throw new IllegalArgumentException("Invalid department: must match a valid system department");
                }

                if (!seenEmails.add(emailVal)) {
                    throw new IllegalArgumentException("Duplicate email in request");
                }

                candidates.add(new RowCandidate(currentRow, nameVal, emailVal, roleVal, departmentVal));

            } catch (IllegalArgumentException e) {
                failed++;
                errors.add("Row " + currentRow + ": " + e.getMessage());
            }
        }

        if (candidates.isEmpty()) {
            return new BulkUploadResponse(total, success, failed, errors);
        }

        // Check which emails already exist globally
        Set<String> candidateEmails = new LinkedHashSet<>();
        for (RowCandidate candidate : candidates) {
            candidateEmails.add(candidate.email());
        }

        Map<String, User> existingUsers = new HashMap<>();
        for (User existingUser : userRepository.findAllByEmailLowerIn(candidateEmails)) {
            existingUsers.put(existingUser.getEmail().toLowerCase(Locale.ROOT), existingUser);
        }

        // Check which emails already have a mapping in THIS tenant
        Set<UUID> candidateUserIds = existingUsers.values().stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        Set<UUID> existingMappedUserIds = new HashSet<>(tenantUserRepository.findUserIdsByTenantIdAndUserIdIn(
                tenant.getId(),
                candidateUserIds
        ));

        Instant createdAt = Instant.now();
        String passwordHash = passwordEncoder.encode(bulkUploadDefaultPassword);
        Boolean autoAssign = request.autoAssignTracks() != null ? request.autoAssignTracks() : true;

        for (RowCandidate candidate : candidates) {
            User user = existingUsers.get(candidate.email());

            if (user != null && existingMappedUserIds.contains(user.getId())) {
                failed++;
                errors.add("Row " + candidate.rowNumber() + ": User already exists in this tenant");
                continue;
            }

            // Create global user if needed
            if (user == null) {
                user = new User();
                user.setId(UUID.randomUUID());
                user.setName(candidate.name());
                user.setEmail(candidate.email());
                user.setPasswordHash(passwordHash);
                user.setAuthProvider("LOCAL");
                user.setIsActive(true);
                user.setCreatedAt(createdAt);
                user = userRepository.save(user);
            }

            // Create tenant mapping
            TenantUser mapping = new TenantUser(user.getId(), tenant.getId(), candidate.role());
            mapping.setDepartment(candidate.department());
            UUID actId = currentActorUserId();
            if (actId != null) {
                mapping.setCreatedBy(actId);
            }
            tenantUserRepository.save(mapping);

            // Auto-assign department tracks if enabled
            if (Boolean.TRUE.equals(autoAssign) && candidate.department() != null) {
                autoAssignDepartmentTracks(user, tenant, candidate.department());
            }

            success++;
        }

        int totalRows = total;
        int successRows = success;
        int failedRows = failed;
        logAdminAction(
                AuditAction.BULK_UPLOAD_USERS,
                "USER",
                tenant.getId().toString(),
                actor -> actor + " processed bulk user upload in " + auditMetadataService.currentTenantLabel()
                        + ": total=" + totalRows + ", success=" + successRows + ", failed=" + failedRows
        );

        return new BulkUploadResponse(total, success, failed, errors);
    }


    // ── Private helpers ──────────────────────────────────────────────────────

    private UserResponse toResponse(User u, TenantUser m) {
        return new UserResponse(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getLocation(),
                m.getRole(),
                m.getDepartment(),
                u.getIsActive(),
                u.getCreatedAt(),
                u.getLastLoginAt()
        );
    }

    private void logAdminAction(
            AuditAction action,
            String entityType,
            String entityId,
            java.util.function.Function<String, String> detailsBuilder
    ) {
        UUID actorId = currentActorUserId();
        if (actorId == null) {
            return;
        }
        String actor = auditMetadataService.describeUserInCurrentTenant(actorId);
        auditService.log(actorId, action, entityType, entityId, detailsBuilder.apply(actor));
    }

    private UUID currentActorUserId() {
        return SecurityUtils.currentUserIdOrNull();
    }

    private void validateBulkUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }
        if (file.getSize() > MAX_BULK_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File size exceeds 2 MB limit");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Only CSV files are supported");
        }
    }

    private void validateBulkUploadDefaults() {
        if (bulkUploadDefaultPassword == null || bulkUploadDefaultPassword.isBlank()) {
            throw new IllegalStateException("Bulk upload default password is not configured");
        }
    }

    private Reader createCsvReader(MultipartFile file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        reader.mark(1);
        if (reader.read() != '\uFEFF') {
            reader.reset();
        }
        return reader;
    }

    private void validateRequiredHeaders(CSVParser csvParser) {
        if (csvParser.getHeaderMap() == null) {
            throw new IllegalArgumentException("CSV must contain name, email, role, and department headers");
        }

        Set<String> normalizedHeaders = new HashSet<>();
        for (String header : csvParser.getHeaderMap().keySet()) {
            normalizedHeaders.add(header.trim().toLowerCase(Locale.ROOT));
        }

        if (!normalizedHeaders.contains("name") ||
                !normalizedHeaders.contains("email") ||
                !normalizedHeaders.contains("role") ||
                !normalizedHeaders.contains("department")) {
            throw new IllegalArgumentException("CSV must contain name, email, role, and department headers");
        }
    }

    private boolean isBlankRow(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void validateCandidate(String name, String email, String role) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name missing");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email missing");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email");
        }
        if (role == null || !ALLOWED_BULK_ROLES.contains(role)) {
            throw new IllegalArgumentException("Invalid role");
        }
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        if (role == null) return null;
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private Department normalizeDepartment(String department) {
        return Department.fromString(department);
    }

    private String normalizeLocation(String location) {
        if (location == null) return null;
        String trimmed = location.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSearchText(String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSearchEmail(String email) {
        return normalizeSearchText(email);
    }

    private void autoAssignDepartmentTracks(User user, Tenant tenant, Department department) {
        List<Track> departmentTracks = trackRepository.findLatestPublishedTracksByDepartment(department);
        for (Track track : departmentTracks) {
            boolean alreadyAssigned = userAssignmentRepository
                    .findByUserIdAndTrackId(user.getId(), track.getId())
                    .isPresent();
            if (!alreadyAssigned) {
                UserAssignment assignment = new UserAssignment();
                assignment.setId(UUID.randomUUID());
                assignment.setUserId(user.getId());
                assignment.setTrackId(track.getId());
                assignment.setAssignedAt(Instant.now());
                assignment.setDueDate(Instant.now().plus(7, ChronoUnit.DAYS));
                assignment.setStatus(AssignmentStatus.ASSIGNED);
                assignment.setContentVersionAtAssignment(track.getVersion());
                assignment.setRequiresRetraining(false);
                userAssignmentRepository.save(assignment);

                // Send email notification for the auto-assignment
                sendTrackAssignedNotification(user, track.getTitle(), tenant.getCompanyName(), tenant.getSlug());
            }
        }
    }

    private void sendTrackAssignedNotification(User user, String trackTitle, String tenantName, String tenantSlug) {
        try {
            String displayName = user.getName() != null ? user.getName() : user.getEmail();
            String message = "You have been assigned a new learning track: <strong>" + trackTitle + "</strong>."
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
            // Log and continue
        }
    }

    private record RowCandidate(
            long rowNumber,
            String name,
            String email,
            String role,
            Department department
    ) {}

    private record TempRowCandidate(
            long rowNumber,
            String name,
            String email,
            String role,
            Department department,
            String departmentRaw,
            List<String> errors
    ) {}
}
