package com.icentric.Icentric.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Routes read-only transactions to a read replica datasource.
 * Activate by setting: app.datasource.replica.enabled=true
 */
@Configuration
@ConditionalOnProperty(name = "app.datasource.replica.enabled", havingValue = "true")
public class ReadReplicaConfig {

    @Bean
    public DataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(30);
        return ds;
    }

    @Bean
    public DataSource replicaDataSource(
            @Value("${app.datasource.replica.url}") String url,
            @Value("${app.datasource.replica.username}") String username,
            @Value("${app.datasource.replica.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(30);
        ds.setReadOnly(true);
        return ds;
    }

    @Primary
    @Bean
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replicaDataSource") DataSource replica) {

        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        ? "replica" : "primary";
            }
        };

        routing.setTargetDataSources(Map.of(
                "primary", primary,
                "replica", replica
        ));
        routing.setDefaultTargetDataSource(primary);
        return routing;
    }
}
