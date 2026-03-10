package com.icentric.Icentric.config;
import com.icentric.Icentric.security.JwtAuthenticationFilter;
import com.icentric.Icentric.security.JwtService;
import com.icentric.Icentric.tenant.TenantFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import javax.sql.DataSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    JwtService jwtService() {
        return new JwtService();
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
            TenantFilter tenantFilter
    ) throws Exception {

        http.csrf(csrf -> csrf.disable());

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/platform/auth/**","/api/v1/platform/tenants/{slug}/impersonate").permitAll()
                .anyRequest().authenticated()
        );

        http.addFilterBefore(jwtFilter,
                org.springframework.security.web.authentication.
                        UsernamePasswordAuthenticationFilter.class);

        http.addFilterAfter(tenantFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}