package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.audit.service.AuditService;
import com.icentric.Icentric.identity.dto.LoginRequest;
import com.icentric.Icentric.identity.dto.LoginResponse;
import com.icentric.Icentric.identity.dto.SelectTenantRequest;
import com.icentric.Icentric.identity.dto.TenantChoice;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.security.RefreshToken;
import com.icentric.Icentric.security.RefreshTokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Global authentication service.
 * <p>
 * Login is now a <b>two-phase</b> process:
 * <ol>
 *   <li>{@link #login} — verify credentials, resolve tenants.</li>
 *   <li>{@link #selectTenant} — (only when multi-tenant) issue JWT for the chosen workspace.</li>
 * </ol>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
    }

    // ── Phase-1: login ──────────────────────────────────────────────────────

    /**
     * Authenticates a user globally (no tenant required in the payload).
     *
     * <ul>
     *   <li>1 tenant  → auto-select, return tokens immediately.</li>
     *   <li>N tenants → return the list of workspaces (no tokens yet).</li>
     *   <li>0 tenants → reject (orphaned global user).</li>
     * </ul>
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {

        // Step 1: global user lookup
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadCredentialsException("Account is deactivated");
        }

        // Step 2: verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // Step 3: resolve tenant memberships
        List<TenantUser> memberships = tenantUserRepository.findByUserId(user.getId());

        if (memberships.isEmpty()) {
            throw new BadCredentialsException("User is not associated with any workspace");
        }

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Step 4: single vs multi-tenant
        if (memberships.size() == 1) {
            TenantUser membership = memberships.get(0);
            Tenant tenant = tenantRepository.findById(membership.getTenantId())
                    .orElseThrow(() -> new IllegalStateException("Tenant not found"));

            return issueTokens(user, tenant.getSlug(), membership.getRole());
        }

        // Multiple tenants → return choices
        List<TenantChoice> choices = memberships.stream()
                .map(m -> {
                    Tenant t = tenantRepository.findById(m.getTenantId())
                            .orElseThrow(() -> new IllegalStateException("Tenant not found"));
                    return new TenantChoice(t.getId(), t.getSlug(), t.getCompanyName(), m.getRole());
                })
                .toList();

        return LoginResponse.multiTenant(choices);
    }

    // ── Phase-2: select-tenant ──────────────────────────────────────────────

    /**
     * Called after login when the user belongs to multiple tenants.
     * The frontend sends the chosen {@code tenantId}; we verify
     * membership and issue the JWT.
     *
     * @param email         the authenticated user's email (from a temporary
     *                      session token or from the original login flow kept
     *                      in memory by the frontend).
     * @param request       contains the selected {@code tenantId}.
     */
    @Transactional
    public LoginResponse selectTenant(String email, SelectTenantRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        TenantUser membership = tenantUserRepository
                .findByUserIdAndTenantId(user.getId(), request.tenantId())
                .orElseThrow(() -> new BadCredentialsException("User is not a member of the selected workspace"));

        Tenant tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        return issueTokens(user, tenant.getSlug(), membership.getRole());
    }

    // ── Token Refresh ───────────────────────────────────────────────────────

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

        return LoginResponse.singleTenant(accessToken, newRefreshToken);
    }

    // ── Logout ──────────────────────────────────────────────────────────────

    /**
     * Revokes the given refresh token (logout).
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private LoginResponse issueTokens(User user, String tenantSlug, String role) {
        String qualifiedRole = "ROLE_" + role;

        String accessToken = jwtService.generateToken(
                user.getEmail(),
                user.getId(),
                qualifiedRole,
                tenantSlug
        );

        String refreshToken = refreshTokenService.create(
                user.getId(),
                user.getEmail(),
                qualifiedRole,
                tenantSlug
        );

        auditService.log(user.getId(), "LOGIN", "USER", user.getId().toString(),
                "User logged in to tenant " + tenantSlug);

        return LoginResponse.singleTenant(accessToken, refreshToken);
    }
}
