package com.icentric.Icentric.config;

import com.icentric.Icentric.identity.TenantHeaderFilter;
import com.icentric.Icentric.security.JwtAuthenticationFilter;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.tenant.TenantFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.sql.DataSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public TenantHeaderFilter tenantHeaderFilter() {
        return new TenantHeaderFilter();
    }

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
            TenantHeaderFilter tenantHeaderFilter
    ) throws Exception {

        http.csrf(csrf -> csrf.disable());

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/v1/platform/auth/login",
                        "/api/v1/platform/auth/mfa/enroll",
                        "/api/v1/auth/login",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                ).permitAll()

                .requestMatchers("/api/v1/platform/tenants/*/impersonate")
                .hasAuthority("ROLE_PLATFORM_ADMIN")

                .requestMatchers("/api/v1/platform/content/**")
                .hasAuthority("ROLE_PLATFORM_ADMIN")

                .requestMatchers("/api/v1/admin/**")
                .hasAnyAuthority("ROLE_ADMIN","ROLE_SUPER_ADMIN")

                .requestMatchers("/api/v1/lessons/**")
                .hasAuthority("ROLE_LEARNER")

                .requestMatchers("/api/v1/learner/**")
                .hasAuthority("ROLE_LEARNER")

                .requestMatchers("/api/v1/lessons/**")
                .hasAuthority("ROLE_LEARNER")
                .requestMatchers("/api/v1/admin/**")
                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                .anyRequest().authenticated()
        );

        /*
         FILTER ORDER
         */

        // 1️⃣ Resolve tenant from header
        http.addFilterBefore(
                tenantHeaderFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        // 2️⃣ Parse JWT
        http.addFilterAfter(
                jwtFilter,
                TenantHeaderFilter.class
        );

        // 3️⃣ Switch schema
        http.addFilterAfter(
                tenantFilter,
                JwtAuthenticationFilter.class
        );

        return http.build();
    }
}