package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.icentric.Icentric.tenant.TenantSchemaService;

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

    @Transactional
    public User createUser(CreateUserRequest request) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        User user = new User();

        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setDepartment(request.department());
        user.setIsActive(true);
        user.setCreatedAt(Instant.now());

        return repository.save(user);
    }
}
