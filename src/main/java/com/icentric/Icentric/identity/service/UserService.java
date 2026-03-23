package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.dto.BulkUploadResponse;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.dto.UserResponse;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.exception.UserNotFoundException;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
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

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {
    private static final long MAX_BULK_UPLOAD_BYTES = 2L * 1024 * 1024;
    private static final int BATCH_SIZE = 100;
    private static final Set<String> ALLOWED_BULK_ROLES = Set.of("LEARNER", "ADMIN", "SUPER_ADMIN");
    private static final String BULK_UPLOAD_TEMPLATE_HEADER = "email,role,department";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TenantSchemaService tenantSchemaService;
    private final String bulkUploadDefaultPassword;

    public UserService(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            TenantSchemaService tenantSchemaService,
            @Value("${app.bulk-upload.default-password:ChangeMe@123}") String bulkUploadDefaultPassword
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tenantSchemaService = tenantSchemaService;
        this.bulkUploadDefaultPassword = bulkUploadDefaultPassword;
    }

    // ✅ CREATE USER — returns UserResponse (never exposes passwordHash)
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setDepartment(request.department());
        user.setIsActive(true);
        user.setCreatedAt(Instant.now());

        User saved = repository.save(user);

        return toResponse(saved);
    }

    // ✅ GET USERS — @Transactional(readOnly) ensures SET LOCAL search_path stays in scope
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(
            String department,
            String role,
            Boolean isActive,
            Pageable pageable
    ) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        Page<User> users;

        boolean hasDept   = department != null;
        boolean hasRole   = role != null;
        boolean hasActive = isActive != null;

        if (hasDept && hasRole && hasActive) {
            users = repository.findByDepartmentAndRoleAndIsActive(department, role, isActive, pageable);
        } else if (hasDept && hasRole) {
            users = repository.findByDepartmentAndRole(department, role, pageable);
        } else if (hasDept && hasActive) {
            users = repository.findByDepartmentAndIsActive(department, isActive, pageable);
        } else if (hasRole && hasActive) {
            users = repository.findByRoleAndIsActive(role, isActive, pageable);
        } else if (hasDept) {
            users = repository.findByDepartment(department, pageable);
        } else if (hasRole) {
            users = repository.findByRole(role, pageable);
        } else if (hasActive) {
            users = repository.findByIsActive(isActive, pageable);
        } else {
            users = repository.findAll(pageable);
        }

        return users.map(this::toResponse);
    }

    // ✅ UPDATE USER
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        var user = repository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.department() != null) {
            user.setDepartment(request.department());
        }
        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }

        return toResponse(repository.save(user));
    }

    // ✅ DEACTIVATE USER (soft delete)
    @Transactional
    public void deactivateUser(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        var user = repository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setIsActive(false);
        repository.save(user);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getRole(),
                u.getDepartment(),
                u.getIsActive(),
                u.getCreatedAt()
        );
    }

    public String getBulkUploadTemplateCsv() {
        return BULK_UPLOAD_TEMPLATE_HEADER + "\n";
    }

    @Transactional
    public BulkUploadResponse bulkUploadUsers(MultipartFile file) {
        tenantSchemaService.applyCurrentTenantSearchPath();
        validateBulkUploadFile(file);
        validateBulkUploadDefaults();

        int total = 0;
        int success = 0;
        int failed = 0;

        List<String> errors = new ArrayList<>();
        List<RowCandidate> candidates = new ArrayList<>();
        Set<String> seenEmailsInFile = new HashSet<>();

        try (
                Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
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
                    String email = normalizeEmail(record.get("email"));
                    String role = normalizeRole(record.get("role"));
                    String department = normalizeDepartment(record.get("department"));

                    validateCandidate(email, role);

                    if (!seenEmailsInFile.add(email)) {
                        throw new IllegalArgumentException("Duplicate email in file");
                    }

                    candidates.add(new RowCandidate(rowNumber, email, role, department));

                } catch (IllegalArgumentException e) {

                    failed++;
                    errors.add("Row " + rowNumber + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("CSV processing failed", e);
        }

        if (candidates.isEmpty()) {
            return new BulkUploadResponse(total, success, failed, errors);
        }

        Set<String> candidateEmails = new LinkedHashSet<>();
        for (RowCandidate candidate : candidates) {
            candidateEmails.add(candidate.email());
        }

        Set<String> existingEmails = new HashSet<>();
        for (User existingUser : repository.findAllByEmailLowerIn(candidateEmails)) {
            existingEmails.add(normalizeEmail(existingUser.getEmail()));
        }

        Instant createdAt = Instant.now();
        String passwordHash = passwordEncoder.encode(bulkUploadDefaultPassword);
        List<User> batch = new ArrayList<>(BATCH_SIZE);

        for (RowCandidate candidate : candidates) {
            if (existingEmails.contains(candidate.email())) {
                failed++;
                errors.add("Row " + candidate.rowNumber() + ": User already exists");
                continue;
            }

            User user = new User();
            user.setId(UUID.randomUUID());
            user.setEmail(candidate.email());
            user.setPasswordHash(passwordHash);
            user.setRole(candidate.role());
            user.setDepartment(candidate.department());
            user.setIsActive(true);
            user.setCreatedAt(createdAt);

            batch.add(user);
            success++;

            if (batch.size() == BATCH_SIZE) {
                repository.saveAll(batch);
                repository.flush();
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            repository.saveAll(batch);
            repository.flush();
        }

        return new BulkUploadResponse(total, success, failed, errors);
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

    private void validateRequiredHeaders(CSVParser csvParser) {
        if (csvParser.getHeaderMap() == null) {
            throw new IllegalArgumentException("CSV must contain email, role, and department headers");
        }

        Set<String> normalizedHeaders = new HashSet<>();
        for (String header : csvParser.getHeaderMap().keySet()) {
            normalizedHeaders.add(header.trim().toLowerCase(Locale.ROOT));
        }

        if (!normalizedHeaders.contains("email") ||
                !normalizedHeaders.contains("role") ||
                !normalizedHeaders.contains("department")) {
            throw new IllegalArgumentException("CSV must contain email, role, and department headers");
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

    private void validateCandidate(String email, String role) {
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

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDepartment(String department) {
        if (department == null) {
            return null;
        }
        String trimmed = department.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record RowCandidate(
            long rowNumber,
            String email,
            String role,
            String department
    ) {}
}
