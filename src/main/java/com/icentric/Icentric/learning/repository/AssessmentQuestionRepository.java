package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, UUID> {
    List<AssessmentQuestion> findByAssessmentIdOrderBySortOrder(String assessmentId);
    List<AssessmentQuestion> findByAssessmentIdAndQuestionIdIn(String assessmentId, List<String> questionIds);
}
