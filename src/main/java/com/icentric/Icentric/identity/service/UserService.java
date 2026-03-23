package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.dto.UserResponse;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.exception.UserNotFoundException;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TenantSchemaService tenantSchemaService;

    public UserService(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            TenantSchemaService tenantSchemaService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tenantSchemaService = tenantSchemaService;
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
}
