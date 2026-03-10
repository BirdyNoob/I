package com.icentric.Icentric.platform.tenant.service;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class TenantProvisioningService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public TenantProvisioningService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public void provisionTenantSchema(String slug) {

        String schemaName = "tenant_" + slug;

        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration/tenant")
                .load()
                .migrate();
    }
}
