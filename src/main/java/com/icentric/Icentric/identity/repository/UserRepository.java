package com.icentric.Icentric.identity.repository;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the global {@code system.users} table.
 * Queries here are schema-independent because the entity is
 * annotated with {@code @Table(schema = "system")}.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE lower(u.email) IN :emails")
    List<User> findAllByEmailLowerIn(@Param("emails") Collection<String> emails);

    List<User> findByIdIn(List<UUID> ids);

    @Query("""
        SELECT u FROM User u
        WHERE (:name IS NULL OR LOWER(u.name) LIKE CONCAT('%', LOWER(:name), '%'))
          AND (:email IS NULL OR LOWER(u.email) LIKE CONCAT(LOWER(:email), '%'))
    """)
    List<User> searchByNameOrEmail(
            @Param("name") String name,
            @Param("email") String email
    );

    @Query("""
        SELECT new com.icentric.Icentric.identity.dto.UserResponse(
            u.id, u.name, u.email, u.location, tu.role, tu.department, u.isActive, u.createdAt, u.lastLoginAt
        )
        FROM User u
        JOIN TenantUser tu ON u.id = tu.userId
        WHERE tu.tenantId = :tenantId
          AND (:department IS NULL OR tu.department = :department)
          AND (:role IS NULL OR tu.role = :role)
          AND (:isActive IS NULL OR u.isActive = :isActive)
    """)
    Page<com.icentric.Icentric.identity.dto.UserResponse> findTenantUsers(
            @Param("tenantId") UUID tenantId,
            @Param("department") Department department,
            @Param("role") String role,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );

    @Query("""
        SELECT new com.icentric.Icentric.identity.dto.UserResponse(
            u.id, u.name, u.email, u.location, tu.role, tu.department, u.isActive, u.createdAt, u.lastLoginAt
        )
        FROM User u
        JOIN TenantUser tu ON u.id = tu.userId
        WHERE tu.tenantId = :tenantId
          AND (:department IS NULL OR tu.department = :department)
          AND (:role IS NULL OR tu.role = :role)
          AND (:isActive IS NULL OR u.isActive = :isActive)
          AND (:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))
          AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT(CAST(:email AS string), '%')))
    """)
    Page<com.icentric.Icentric.identity.dto.UserResponse> searchTenantUsers(
            @Param("tenantId") UUID tenantId,
            @Param("department") Department department,
            @Param("role") String role,
            @Param("isActive") Boolean isActive,
            @Param("name") String name,
            @Param("email") String email,
            Pageable pageable
    );
}
