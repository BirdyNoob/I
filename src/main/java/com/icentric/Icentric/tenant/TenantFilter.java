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

            // Convert slug → schema
            String schema;
            if ("system".equals(tenant)) {
                schema = "system";
            } else {
                if (!tenant.matches("[a-zA-Z0-9_-]+")) {
                    throw new IllegalArgumentException("Invalid tenant slug: " + tenant);
                }
                schema = "tenant_" + tenant;
            }

            Connection connection = DataSourceUtils.getConnection(dataSource);

            try (Statement statement = connection.createStatement()) {

                statement.execute("SET search_path TO " + schema);

                // Debug log
                System.out.println("Current tenant schema = " + schema);

                filterChain.doFilter(request, response);

            } catch (Exception e) {

                throw new ServletException("Tenant schema switch failed", e);

            } finally {
                try (Statement reset = connection.createStatement()) {
                    reset.execute("SET search_path TO public");
                } catch (Exception ignored) {
                    // Ignore reset failures and still release the connection.
                }

                DataSourceUtils.releaseConnection(connection, dataSource);
            }
            return;
        }

        filterChain.doFilter(request, response);
    }
}
