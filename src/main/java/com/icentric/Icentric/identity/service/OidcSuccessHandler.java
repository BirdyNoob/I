package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Called by Spring Security after a successful OIDC login.
 *
 * <p>Resolves the user's workspace(s) from {@code tenant_users} automatically
 * — the user never needs to enter a slug or tenant identifier manually.
 *
 * <ul>
 *   <li>Single workspace  → issues Icentric JWT immediately, redirects to dashboard.</li>
 *   <li>Multiple workspaces → issues a short-lived temp token, redirects to workspace picker.</li>
 * </ul>
 */
@Slf4j
@Component
public class OidcSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${icentric.frontend.url}")
    private String frontendUrl;

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public OidcSuccessHandler(UserRepository userRepository,
                               TenantUserRepository tenantUserRepository,
                               TenantRepository tenantRepository,
                               JwtService jwtService,
                               RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String email = oidcUser.getEmail();

        // User is guaranteed to exist here — IcentricOidcUserService already verified it
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "User disappeared between OIDC validation and success handler: " + email));

        // Update last login timestamp
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Resolve workspace(s) — no slug input needed from the user
        List<TenantUser> memberships = tenantUserRepository.findByUserId(user.getId());

        if (memberships.size() == 1) {
            // ── Single workspace: auto-login completely ──────────────────────
            TenantUser membership = memberships.get(0);
            Tenant tenant = tenantRepository.findById(membership.getTenantId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Tenant not found for tenantId=" + membership.getTenantId()));

            String qualifiedRole = "ROLE_" + membership.getRole();

            String accessToken  = jwtService.generateToken(
                    email, user.getId(), qualifiedRole, tenant.getSlug());
            String refreshToken = refreshTokenService.create(
                    user.getId(), email, qualifiedRole, tenant.getSlug());

            log.info("SSO single-workspace login: userId={}, tenant={}, role={}",
                    user.getId(), tenant.getSlug(), membership.getRole());

            String redirectUrl = frontendUrl + "/sso/callback"
                    + "?accessToken="  + encode(accessToken)
                    + "&refreshToken=" + encode(refreshToken)
                    + "&role="         + encode(membership.getRole());

            getRedirectStrategy().sendRedirect(request, response, redirectUrl);

        } else {
            // ── Multiple workspaces: redirect to workspace picker ─────────────
            // Issue a short-lived (5-min) pending token — frontend uses it to call
            // POST /api/v1/auth/select-tenant (same endpoint as multi-tenant local login)
            String pendingToken = jwtService.generateToken(
                    email, user.getId(), "ROLE_SSO_PENDING", "none");

            log.info("SSO multi-workspace: userId={}, workspaces={}, redirecting to picker",
                    user.getId(), memberships.size());

            String redirectUrl = frontendUrl + "/sso/select-tenant"
                    + "?token=" + encode(pendingToken);

            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
