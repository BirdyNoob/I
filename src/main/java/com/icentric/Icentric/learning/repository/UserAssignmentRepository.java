package com.icentric.Icentric.learning.repository;
import com.icentric.Icentric.learning.entity.UserAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface UserAssignmentRepository
        extends JpaRepository<UserAssignment, UUID> {

    List<UserAssignment> findByUserId(UUID userId);
    long count();
    long countByStatus(String status);

}
