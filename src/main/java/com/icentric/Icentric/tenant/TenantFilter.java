package com.icentric.Icentric.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;

public class TenantFilter extends OncePerRequestFilter {

    private final DataSource dataSource;

    public TenantFilter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String tenant = TenantContext.getTenant();

        if (tenant != null) {
            // Fix #4: Use Spring's DataSourceUtils so the connection stays managed
            // by the transaction infrastructure; use try-with-resources for Statement.
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try (Statement statement = connection.createStatement()) {
                if ("system".equals(tenant)) {
                    statement.execute("SET search_path TO system");
                } else {
                    // Restrict slug to alphanumerics/hyphens to prevent SQL injection
                    if (!tenant.matches("[a-zA-Z0-9_-]+")) {
                        throw new IllegalArgumentException("Invalid tenant slug: " + tenant);
                    }
                    statement.execute("SET search_path TO tenant_" + tenant);
                }
            } catch (Exception e) {
                DataSourceUtils.releaseConnection(connection, dataSource);
                throw new ServletException("Tenant schema switch failed", e);
            }
            // Note: don't release the connection here — Spring's transaction
            // infrastructure (or the connection pool) will handle it.
        }
        System.out.println("Current tenant schema = " + tenant);

        filterChain.doFilter(request, response);
    }
}
