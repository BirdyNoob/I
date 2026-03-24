package com.icentric.Icentric.learning.repository;
import com.icentric.Icentric.learning.entity.UserAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
    long countByStatus(String status);
    List<UserAssignment> findAll();
    Optional<UserAssignment> findByUserIdAndTrackId(UUID userId, UUID trackId);
    List<UserAssignment> findByTrackId(UUID trackId);
    @Query("""
SELECT ua, u.email, u.department
FROM UserAssignment ua
JOIN User u ON ua.userId = u.id
WHERE (:department IS NULL OR u.department = :department)
AND (:status IS NULL OR ua.status = :status)
AND (:trackId IS NULL OR ua.trackId = :trackId)
""")
    List<Object[]> fetchCompletionData(
            String department,
            String status,
            UUID trackId
    );
    @Query("""
SELECT ua, u.email, u.department
FROM UserAssignment ua
JOIN User u ON ua.userId = u.id
WHERE ua.status IN ('FAILED', 'OVERDUE')
AND (:department IS NULL OR u.department = :department)
AND (:trackId IS NULL OR ua.trackId = :trackId)
""")
    List<Object[]> fetchRiskData(
            String department,
            UUID trackId
    );
    @Query("""
SELECT ua FROM UserAssignment ua
WHERE ua.status IN ('ASSIGNED','IN_PROGRESS')
AND ua.dueDate IS NOT NULL
""")
    List<UserAssignment> findActiveAssignments();

    @Query("""
SELECT ua FROM UserAssignment ua
WHERE ua.status = 'OVERDUE'
AND ua.dueDate IS NOT NULL
""")
    List<UserAssignment> findOverdueAssignments();
    @Query("""
SELECT COUNT(ua)
FROM UserAssignment ua
WHERE ua.status = 'COMPLETED'
""")
    long countCompleted();
    @Query("""
SELECT u.department, COUNT(ua), 
SUM(CASE WHEN ua.status = 'COMPLETED' THEN 1 ELSE 0 END)
FROM UserAssignment ua
JOIN User u ON ua.userId = u.id
GROUP BY u.department
""")
    List<Object[]> fetchDepartmentStats();

    long countByAssignedAtAfter(Instant assignedAt);

    long countByDueDateBetweenAndStatusIn(Instant dueDateFrom, Instant dueDateTo, List<String> statuses);
}
