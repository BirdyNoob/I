package com.icentric.Icentric.platform.tenant.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantUserBootstrapService {

    private final JdbcTemplate jdbcTemplate;

    public TenantUserBootstrapService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createSuperAdmin(String slug, String email) {

        String schema = "tenant_" + slug;

        String sql = """
        INSERT INTO %s.users (id, email, role, created_at)
        VALUES (?, ?, 'super_admin', now())
        """.formatted(schema);

        jdbcTemplate.update(sql,
                UUID.randomUUID(),
                email
        );
    }
}
