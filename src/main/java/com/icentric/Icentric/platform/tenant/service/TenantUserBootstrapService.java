package com.icentric.Icentric.platform.tenant.service;

import com.icentric.Icentric.common.mail.EmailService;
import com.icentric.Icentric.identity.entity.TenantUser;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.repository.TenantUserRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.platform.tenant.entity.Tenant;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Bootstraps the super-admin user when a new tenant is created.
 * <p>
 * Under the Global User Registry model:
 * <ol>
 *   <li>Check if a global user already exists for the given email.</li>
 *   <li>If not, create one in {@code system.users}.</li>
 *   <li>Insert a {@code tenant_users} mapping granting SUPER_ADMIN to the new tenant.</li>
 * </ol>
 */
@Service
public class TenantUserBootstrapService {

    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String publicBaseUrl;

    public TenantUserBootstrapService(
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * Creates or reuses the global user and maps them as SUPER_ADMIN of the new tenant.
     *
     * @param slug          the tenant slug (used to look up the tenant record)
     * @param email         admin email
     * @param rawPassword   plain-text password (only used if the user doesn't exist yet)
     */
    @Transactional
    public void createSuperAdmin(String slug, String email, String rawPassword) {

        // Resolve tenant
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + slug));

        // Upsert global user
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setId(UUID.randomUUID());
                    newUser.setEmail(email);
                    newUser.setPasswordHash(passwordEncoder.encode(rawPassword));
                    newUser.setAuthProvider("LOCAL");
                    newUser.setIsActive(true);
                    newUser.setCreatedAt(Instant.now());
                    return userRepository.save(newUser);
                });

        // Create tenant membership
        tenantUserRepository.findByUserIdAndTenantId(user.getId(), tenant.getId())
                .ifPresentOrElse(
                        existing -> { /* already mapped — nothing to do */ },
                        () -> {
                            TenantUser mapping = new TenantUser(user.getId(), tenant.getId(), "SUPER_ADMIN");
                            tenantUserRepository.save(mapping);
                        }
                );

        // Send Welcome Email
        Map<String, Object> variables = Map.of(
                "userName", email, // Using email as name since name is not captured during tenant signup
                "tenantName", tenant.getCompanyName(),
                "setupUrl", publicBaseUrl + "/setup?tenant=" + slug,
                "loginUrl", publicBaseUrl + "/login?tenant=" + slug
        );

        emailService.sendTemplateEmail(
                email,
                "Welcome to AISafe - Administrator Account Created",
                "AISafe_Email_TenantAdmin_Welcome",
                variables
        );
    }
}
