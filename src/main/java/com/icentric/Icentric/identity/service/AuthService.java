package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.dto.LoginRequest;
import com.icentric.Icentric.identity.dto.LoginResponse;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantSchemaService tenantSchemaService;

    public AuthService(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TenantSchemaService tenantSchemaService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tenantSchemaService = tenantSchemaService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String tenant, LoginRequest request) {
        TenantContext.setTenant(tenant);
        tenantSchemaService.applyCurrentTenantSearchPath();

        User user = repository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtService.generateToken(
                user.getEmail(),
                user.getId(),
                "ROLE_" + user.getRole(),
                tenant
        );

        return new LoginResponse(token);
    }
}
