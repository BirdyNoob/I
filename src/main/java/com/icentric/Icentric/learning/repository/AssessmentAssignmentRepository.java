package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.AssessmentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentAssignmentRepository extends JpaRepository<AssessmentAssignment, UUID> {
    List<AssessmentAssignment> findByUserId(UUID userId);
}
