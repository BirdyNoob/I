package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.dto.LoginRequest;
import com.icentric.Icentric.identity.dto.LoginResponse;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.security.RefreshToken;
import com.icentric.Icentric.security.RefreshTokenService;
import com.icentric.Icentric.tenant.TenantContext;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository repository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantSchemaService tenantSchemaService;
    private final AuditService auditService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository repository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TenantSchemaService tenantSchemaService,
            AuditService auditService,
            RefreshTokenService refreshTokenService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tenantSchemaService = tenantSchemaService;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Authenticates a tenant user and returns an access + refresh token pair.
     * Tenant slug now comes from the request body (no more X-Tenant header).
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String tenant = request.tenantSlug();

        TenantContext.setTenant(tenant);
        tenantSchemaService.applyCurrentTenantSearchPath();

        User user = repository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String role = "ROLE_" + user.getRole();

        String accessToken = jwtService.generateToken(
                user.getEmail(),
                user.getId(),
                role,
                tenant
        );

        String refreshToken = refreshTokenService.create(
                user.getId(),
                user.getEmail(),
                role,
                tenant
        );

        auditService.log(user.getId(), "LOGIN", "USER", user.getId().toString(),
                "User logged in to tenant " + tenant);

        return new LoginResponse(accessToken, refreshToken);
    }

    /**
     * Exchanges a valid refresh token for a new access + refresh token pair.
     * The old refresh token is revoked (rotation).
     */
    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenService.validate(rawRefreshToken);

        // Revoke old token (rotation)
        refreshTokenService.revoke(rawRefreshToken);

        String accessToken = jwtService.generateToken(
                stored.getEmail(),
                stored.getUserId(),
                stored.getRole(),
                stored.getTenantSlug()
        );

        String newRefreshToken = refreshTokenService.create(
                stored.getUserId(),
                stored.getEmail(),
                stored.getRole(),
                stored.getTenantSlug()
        );

        return new LoginResponse(accessToken, newRefreshToken);
    }

    /**
     * Revokes the given refresh token (logout).
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }
}
