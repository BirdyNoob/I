package com.icentric.Icentric.identity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Junction table that links a global {@link User} to a specific
 * tenant with a role and (optional) department.
 * Lives in the {@code system} schema.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "tenant_users", schema = "system",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "tenant_id"}),
       indexes = {
           @Index(name = "idx_tu_tenant_user", columnList = "tenant_id, user_id")
       })
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** LEARNER | ADMIN | SUPER_ADMIN */
    @Column(nullable = false)
    private String role;

    private String department;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    public TenantUser(UUID userId, UUID tenantId, String role) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role;
    }
}
