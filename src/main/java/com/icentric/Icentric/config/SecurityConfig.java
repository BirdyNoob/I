package com.icentric.Icentric.config;

import com.icentric.Icentric.identity.service.IcentricOidcUserService;
import com.icentric.Icentric.identity.service.OidcFailureHandler;
import com.icentric.Icentric.identity.service.OidcSuccessHandler;
import com.icentric.Icentric.security.JwtAuthenticationFilter;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.tenant.TenantFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.sql.DataSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    TenantFilter tenantFilter(DataSource dataSource) {
        return new TenantFilter(dataSource);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            TenantFilter tenantFilter,
            IcentricOidcUserService oidcUserService,
            OidcSuccessHandler oidcSuccessHandler,
            OidcFailureHandler oidcFailureHandler
    ) throws Exception {

        http.csrf(csrf -> csrf.disable());
        http.cors(Customizer.withDefaults());

        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Public endpoints (no JWT required)
                .requestMatchers(
                        "/api/v1/platform/auth/login",
                        "/api/v1/platform/auth/mfa/enroll",
                        "/api/v1/platform/auth/refresh",
                        "/api/v1/platform/auth/logout",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/public/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        // ── SSO / OIDC endpoints ──────────────────────────────
                        "/oauth2/authorization/**",   // Initiates SSO redirect (Google/Microsoft)
                        "/login/oauth2/code/**"        // OIDC callback from provider
                ).permitAll()

                // Platform Admin only
                .requestMatchers("/api/v1/platform/tenants/*/impersonate")
                .hasAuthority("ROLE_PLATFORM_ADMIN")

                .requestMatchers("/api/v1/platform/content/**")
                .hasAuthority("ROLE_PLATFORM_ADMIN")

                .requestMatchers("/api/v1/platform/**")
                .hasAuthority("ROLE_PLATFORM_ADMIN")

                // Tenant Admin
                .requestMatchers("/api/v1/admin/**")
                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                // Learner
                .requestMatchers("/api/v1/lessons/**")
                .hasAuthority("ROLE_LEARNER")

                .requestMatchers("/api/v1/learner/**")
                .hasAuthority("ROLE_LEARNER")

                .requestMatchers("/api/v1/notifications/**")
                .hasAuthority("ROLE_LEARNER")

                .requestMatchers("/api/v1/simulations/**")
                .hasAuthority("ROLE_LEARNER")

                // Everything else requires authentication
                .anyRequest().authenticated()
        );

        // ── OIDC / SSO Login ──────────────────────────────────────────────────
        // Plugs into the existing JWT pipeline: OIDC success issues the same
        // Icentric JWT format — JwtAuthenticationFilter is completely unchanged.
        http.oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                        .oidcUserService(oidcUserService)
                )
                .successHandler(oidcSuccessHandler)
                .failureHandler(oidcFailureHandler)
        );

        /*
         * FILTER ORDER (simplified — TenantHeaderFilter removed)
         * 1️⃣ Parse JWT → sets SecurityContext + TenantContext
         * 2️⃣ Switch DB schema based on TenantContext
         */
        http.addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        http.addFilterAfter(
                tenantFilter,
                JwtAuthenticationFilter.class
        );

        return http.build();
    }
}
