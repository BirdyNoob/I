package com.icentric.Icentric.config;

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
            TenantFilter tenantFilter
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
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
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

                // Everything else requires authentication
                .anyRequest().authenticated()
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
