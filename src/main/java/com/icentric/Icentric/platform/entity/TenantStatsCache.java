package com.icentric.Icentric.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Data
@Entity
@Table(name = "tenant_stats_cache", schema = "system")
public class TenantStatsCache {

    @Id
    @Column(name = "tenant_slug")
    private String tenantSlug;

    @Column(name = "total_users")
    private long totalUsers;

    @Column(name = "total_assignments")
    private long totalAssignments;

    @Column(name = "completed_assignments")
    private long completedAssignments;

    @Column(name = "overdue_assignments")
    private long overdueAssignments;

    @Column(name = "completion_percent")
    private int completionPercent;

    @Column(name = "certs_issued")
    private long certsIssued;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dept_completion", columnDefinition = "jsonb")
    private String deptCompletion;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
