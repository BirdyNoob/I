package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.service.AuditMetadataService;
import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.dto.BulkUploadResponse;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.dto.UserResponse;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.exception.UserNotFoundException;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
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
            UserAssignmentRepository userAssignmentRepository
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
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Tenant tenant = tenantAccessGuard.currentTenant();

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

        return userRepository.findTenantUsers(
                tenant.getId(),
                department,
                role,
                isActive,
                pageable
        );
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

        String normalizedName = normalizeSearchText(name);
        String normalizedEmail = normalizeSearchEmail(email);

        return userRepository.searchTenantUsers(
                tenant.getId(),
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
        return BULK_UPLOAD_TEMPLATE_HEADER + "\n";
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
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object userIdRaw = authentication != null ? authentication.getDetails() : null;
        if (userIdRaw == null) {
            return null;
        }
        return UUID.fromString(userIdRaw.toString());
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
                assignment.setDueDate(null);
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
}
