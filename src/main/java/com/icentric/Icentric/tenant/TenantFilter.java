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
            FilterChain filterChain
    ) throws ServletException, IOException {

        String tenant = TenantContext.getTenant();

        if (tenant != null) {

            try {
                Connection connection =
                        DataSourceUtils.getConnection(dataSource);

                Statement statement = connection.createStatement();

                if ("system".equals(tenant)) {
                    statement.execute("SET search_path TO system");
                } else {
                    statement.execute("SET search_path TO tenant_" + tenant);
                }

            } catch (Exception e) {
                throw new RuntimeException("Tenant switch failed", e);
            }
        }

        filterChain.doFilter(request, response);
    }
}
