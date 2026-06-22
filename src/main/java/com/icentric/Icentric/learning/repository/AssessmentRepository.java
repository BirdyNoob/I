package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, String> {
    Optional<Assessment> findByTrackId(String trackId);
}
