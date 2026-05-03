package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.common.enums.Department;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.entity.UserAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
@Repository
public interface UserAssignmentRepository
        extends JpaRepository<UserAssignment, UUID> {

    List<UserAssignment> findByUserId(UUID userId);
    long count();
    long countByStatus(AssignmentStatus status);
    List<UserAssignment> findAll();
    Optional<UserAssignment> findByUserIdAndTrackId(UUID userId, UUID trackId);
    List<UserAssignment> findByTrackId(UUID trackId);
    List<UserAssignment> findByTrackIdIn(List<UUID> trackIds);
    @Query("""
SELECT ua, u.name, u.email, tu.role, tu.department, t.title
FROM UserAssignment ua
JOIN User u ON ua.userId = u.id
JOIN TenantUser tu ON tu.userId = u.id AND tu.tenantId = :tenantId
JOIN Track t ON ua.trackId = t.id
WHERE (:department IS NULL OR tu.department = :department)
AND (:status IS NULL OR ua.status = :status)
AND (:trackId IS NULL OR ua.trackId = :trackId)
""")
    List<Object[]> fetchCompletionData(
            @Param("tenantId") UUID tenantId,
            @Param("department") Department department,
            @Param("status") AssignmentStatus status,
            @Param("trackId") UUID trackId
    );
    @Query("""
SELECT ua, u.name, u.email, tu.role, tu.department, t.title
FROM UserAssignment ua
JOIN User u ON ua.userId = u.id
JOIN TenantUser tu ON tu.userId = u.id AND tu.tenantId = :tenantId
JOIN Track t ON ua.trackId = t.id
WHERE ua.status IN :statuses
AND (:department IS NULL OR tu.department = :department)
AND (:trackId IS NULL OR ua.trackId = :trackId)
""")
    List<Object[]> fetchRiskData(
            @Param("tenantId") UUID tenantId,
            @Param("statuses") List<AssignmentStatus> statuses,
            @Param("department") Department department,
            @Param("trackId") UUID trackId
    );
    @Query("""
SELECT ua, u.name, u.email, tu.role, tu.department, t.title
FROM UserAssignment ua
JOIN User u ON ua.userId = u.id
JOIN TenantUser tu ON tu.userId = u.id AND tu.tenantId = :tenantId
JOIN Track t ON ua.trackId = t.id
WHERE ua.status IN :statuses
AND (:department IS NULL OR tu.department = :department)
AND (:trackId IS NULL OR ua.trackId = :trackId)
""")
    List<Object[]> fetchReportDataByStatuses(
            @Param("tenantId") UUID tenantId,
            @Param("statuses") List<AssignmentStatus> statuses,
            @Param("department") Department department,
            @Param("trackId") UUID trackId
    );
    List<UserAssignment> findByStatusInAndDueDateIsNotNull(List<AssignmentStatus> statuses);

    List<UserAssignment> findByStatusAndDueDateIsNotNull(AssignmentStatus status);
    @Query("""
SELECT tu.department, COUNT(ua), 
SUM(CASE WHEN ua.status = :completedStatus THEN 1 ELSE 0 END)
FROM UserAssignment ua
JOIN User u ON ua.userId = u.id
JOIN TenantUser tu ON tu.userId = u.id AND tu.tenantId = :tenantId
GROUP BY tu.department
""")
    List<Object[]> fetchDepartmentStats(
            @Param("tenantId") UUID tenantId,
            @Param("completedStatus") AssignmentStatus completedStatus
    );

    long countByAssignedAtAfter(Instant assignedAt);

    long countByDueDateBetweenAndStatusIn(Instant dueDateFrom, Instant dueDateTo, List<AssignmentStatus> statuses);
    @Query("""
SELECT ua FROM UserAssignment ua
WHERE (:status IS NULL OR ua.status = :status)
AND (:trackId IS NULL OR ua.trackId = :trackId)
AND (:userId IS NULL OR ua.userId = :userId)
""")
    Page<UserAssignment> searchAssignments(
            @Param("status") AssignmentStatus status,
            @Param("trackId") UUID trackId,
            @Param("userId") UUID userId,
            Pageable pageable
    );
}
