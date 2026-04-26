package com.icentric.Icentric;

import org.flywaydb.core.Flyway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DebugMigrationRunner implements CommandLineRunner {

    private final DataSource dataSource;

    public DebugMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {

        Flyway.configure()
                .dataSource(dataSource)
                .schemas("tenant_acme")
                .locations("classpath:db/migration/tenant")
                .load()
                .migrate();

        System.out.println("Tenant migrations executed.");
    }
}
