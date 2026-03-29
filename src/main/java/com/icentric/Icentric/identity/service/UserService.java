package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.dto.BulkUploadResponse;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.dto.UserResponse;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.exception.UserNotFoundException;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.tenant.TenantContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bulkUploadDefaultPassword;

    public UserService(
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bulk-upload.default-password:ChangeMe@123}") String bulkUploadDefaultPassword
    ) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.bulkUploadDefaultPassword = bulkUploadDefaultPassword;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Resolves the current tenant from TenantContext.
     */
    private Tenant currentTenant() {
        String slug = TenantContext.getTenant();
        if (slug == null || slug.isBlank()) {
            throw new IllegalStateException("No tenant in context");
        }
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + slug));
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Tenant tenant = currentTenant();

        // Upsert global user
        User user = userRepository.findByEmail(request.email())
                .orElseGet(() -> {
                    User u = new User();
                    u.setId(UUID.randomUUID());
                    u.setName(normalizeName(request.name()));
                    u.setEmail(request.email());
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

        return toResponse(user, mapping);
    }

    // ── READ (paginated, filtered) ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(
            String department,
            String role,
            Boolean isActive,
            Pageable pageable
    ) {
        Tenant tenant = currentTenant();

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
        Tenant tenant = currentTenant();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        TenantUser mapping = tenantUserRepository
                .findByUserIdAndTenantId(userId, tenant.getId())
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Update global fields
        if (request.name() != null) {
            user.setName(normalizeName(request.name()));
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

        return toResponse(user, mapping);
    }

    // ── DEACTIVATE ───────────────────────────────────────────────────────────

    @Transactional
    public void deactivateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setIsActive(false);
        userRepository.save(user);
    }

    // ── SEARCH ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(
            String name,
            String email,
            String department,
            String role,
            Boolean isActive,
            Pageable pageable
    ) {
        Tenant tenant = currentTenant();

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
    public BulkUploadResponse bulkUploadUsers(MultipartFile file) {
        Tenant tenant = currentTenant();
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
                    String departmentVal = normalizeDepartment(record.get("department"));

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
        Set<UUID> existingMappedUserIds = tenantUserRepository.findByTenantId(tenant.getId())
                .stream().map(TenantUser::getUserId).collect(Collectors.toSet());

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

            success++;
        }

        return new BulkUploadResponse(total, success, failed, errors);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private UserResponse toResponse(User u, TenantUser m) {
        return new UserResponse(
                u.getId(),
                u.getName(),
                u.getEmail(),
                m.getRole(),
                m.getDepartment(),
                u.getIsActive(),
                u.getCreatedAt()
        );
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

    private String normalizeDepartment(String department) {
        if (department == null) return null;
        String trimmed = department.trim();
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

    private record RowCandidate(
            long rowNumber,
            String name,
            String email,
            String role,
            String department
    ) {}
}
