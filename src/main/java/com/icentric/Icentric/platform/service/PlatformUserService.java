package com.icentric.Icentric.platform.service;

import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

@Service
public class PlatformUserService {

    private final TenantRepository tenantRepository;
    private final DataSource dataSource;

    public PlatformUserService(
            TenantRepository tenantRepository,
            DataSource dataSource
    ) {
        this.tenantRepository = tenantRepository;
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> getTenantUsers(UUID tenantId) {

        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        String schema = "tenant_" + tenant.getSlug();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 🔥 switch schema manually
            stmt.execute("SET search_path TO " + schema);

            ResultSet rs = stmt.executeQuery("""
                SELECT id, name, email, role, department, is_active
                FROM users
            """);

            List<Map<String, Object>> users = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> u = new HashMap<>();
                u.put("id", rs.getObject("id"));
                u.put("name", rs.getString("name"));
                u.put("email", rs.getString("email"));
                u.put("role", rs.getString("role"));
                u.put("department", rs.getString("department"));
                u.put("isActive", rs.getBoolean("is_active"));
                users.add(u);
            }

            return users;

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch tenant users", e);
        }
    }
}
