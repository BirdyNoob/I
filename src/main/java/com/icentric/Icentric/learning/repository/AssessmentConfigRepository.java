package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.AssessmentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssessmentConfigRepository extends JpaRepository<AssessmentConfig, String> {
    List<AssessmentConfig> findByTrackId(String trackId);
}
