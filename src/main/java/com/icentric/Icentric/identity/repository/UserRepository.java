package com.icentric.Icentric.identity.repository;

import com.icentric.Icentric.identity.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    @Query("SELECT u FROM User u WHERE lower(u.email) IN :emails")
    List<User> findAllByEmailLowerIn(Collection<String> emails);

    // Single-filter queries
    Page<User> findByDepartment(String department, Pageable pageable);
    Page<User> findByRole(String role, Pageable pageable);
    Page<User> findByIsActive(Boolean isActive, Pageable pageable);

    // Two-filter combinations
    Page<User> findByDepartmentAndRole(String department, String role, Pageable pageable);
    Page<User> findByDepartmentAndIsActive(String department, Boolean isActive, Pageable pageable);
    Page<User> findByRoleAndIsActive(String role, Boolean isActive, Pageable pageable);

    // Three-filter combination
    Page<User> findByDepartmentAndRoleAndIsActive(
            String department, String role, Boolean isActive, Pageable pageable
    );
    List<User> findByDepartment(String department);
    List<User> findByIdIn(List<UUID> ids);
    long count();
    @Query("""
SELECT u FROM User u
WHERE (:name IS NULL OR LOWER(u.name) LIKE CONCAT('%', LOWER(:name), '%'))
AND (:email IS NULL OR LOWER(u.email) LIKE CONCAT(LOWER(:email), '%'))
AND (:department IS NULL OR u.department = :department)
AND (:role IS NULL OR u.role = :role)
AND (:isActive IS NULL OR u.isActive = :isActive)
""")
    Page<User> searchUsers(
            @Param("name") String name,
            @Param("email") String email,
            @Param("department") String department,
            @Param("role") String role,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
