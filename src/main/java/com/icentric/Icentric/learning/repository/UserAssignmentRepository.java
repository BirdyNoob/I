package com.icentric.Icentric.learning.repository;
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
            AssignmentStatus status,
            UUID trackId
    );
    @Query("""
SELECT ua, u.email, u.department
FROM UserAssignment ua
JOIN User u ON ua.userId = u.id
WHERE ua.status IN :statuses
AND (:department IS NULL OR u.department = :department)
AND (:trackId IS NULL OR ua.trackId = :trackId)
""")
    List<Object[]> fetchRiskData(
            @Param("statuses") List<AssignmentStatus> statuses,
            String department,
            UUID trackId
    );
    List<UserAssignment> findByStatusInAndDueDateIsNotNull(List<AssignmentStatus> statuses);

    List<UserAssignment> findByStatusAndDueDateIsNotNull(AssignmentStatus status);
    @Query("""
SELECT u.department, COUNT(ua), 
SUM(CASE WHEN ua.status = :completedStatus THEN 1 ELSE 0 END)
FROM UserAssignment ua
JOIN User u ON ua.userId = u.id
GROUP BY u.department
""")
    List<Object[]> fetchDepartmentStats(@Param("completedStatus") AssignmentStatus completedStatus);

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
