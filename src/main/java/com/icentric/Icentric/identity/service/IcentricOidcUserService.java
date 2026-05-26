package com.icentric.Icentric.identity.service;

import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OIDC user resolution for Icentric SSO login.
 *
 * <p><b>Rules (in order):</b>
 * <ol>
 *   <li>Try to find user by stable provider subject (returning SSO user).</li>
 *   <li>Fall back to email lookup (first-time SSO for a pre-onboarded user).</li>
 *   <li>If not found at all → blocked. Only admin-onboarded users can log in.</li>
 *   <li>If found but deactivated → blocked.</li>
 *   <li>If found but not linked to any tenant → blocked.</li>
 *   <li>On first SSO login: bind the provider subject to the user record for future fast lookups.</li>
 * </ol>
 *
 * <p><b>No JIT provisioning.</b> We never create a new user here.
 * The user must have been onboarded by an admin via the Icentric API first.
 */
@Slf4j
@Service
public class IcentricOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;

    public IcentricOidcUserService(UserRepository userRepository,
                                   TenantUserRepository tenantUserRepository) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // Step 1: Fetch id_token + userinfo from provider (Google / Microsoft)
        OidcUser oidcUser = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration()
                                     .getRegistrationId()
                                     .toUpperCase();   // "GOOGLE" or "MICROSOFT"
        String subject  = oidcUser.getSubject();       // stable provider ID (never changes)
        String email    = oidcUser.getEmail();

        log.info("SSO login attempt: provider={}, email={}", provider, email);

        // Step 2: Try matching by stable subject first (returning SSO user — fastest path)
        User user = userRepository
                .findByAuthProviderAndProviderSubject(provider, subject)
                .orElseGet(() -> {
                    // Step 3: Fall back to email lookup (first-time SSO login)
                    // Email must already exist — we never create accounts here
                    return userRepository.findByEmail(email)
                            .orElseThrow(() -> {
                                log.warn("SSO blocked — email not found in system: provider={}, email={}", provider, email);
                                return new OAuth2AuthenticationException(
                                        new OAuth2Error("user_not_provisioned"),
                                        "Your account is not provisioned in Icentric. " +
                                        "Please contact your administrator to request access."
                                );
                            });
                });

        // Step 4: Account must be active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("SSO blocked — account deactivated: userId={}, email={}", user.getId(), email);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_deactivated"),
                    "Your account has been deactivated. Please contact your administrator."
            );
        }

        // Step 5: Must be linked to at least one tenant workspace
        if (tenantUserRepository.findByUserId(user.getId()).isEmpty()) {
            log.warn("SSO blocked — no workspace membership: userId={}, email={}", user.getId(), email);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("no_workspace_access"),
                    "Your account is not linked to any workspace. " +
                    "Please contact your administrator."
            );
        }

        // Step 6: Bind provider subject on first SSO login (for fast lookups going forward)
        if (user.getProviderSubject() == null) {
            log.info("Binding SSO provider to user: userId={}, provider={}", user.getId(), provider);
            user.setAuthProvider(provider);
            user.setProviderSubject(subject);
            userRepository.save(user);
        }

        log.info("SSO login authorised: userId={}, provider={}, email={}", user.getId(), provider, email);
        return oidcUser;
    }
}
