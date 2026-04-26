package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.AssessmentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssessmentConfigRepository extends JpaRepository<AssessmentConfig, String> {
    // Custom query methods can be added here if needed, e.g. finding by trackId
}
