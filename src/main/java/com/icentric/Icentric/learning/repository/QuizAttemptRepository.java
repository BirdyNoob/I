package com.icentric.Icentric.learning.repository;

import com.icentric.Icentric.learning.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QuizAttemptRepository
        extends JpaRepository<QuizAttempt, UUID> {
}
