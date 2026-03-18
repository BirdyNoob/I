package com.icentric.Icentric.content.repository;

import com.icentric.Icentric.content.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface QuestionRepository
        extends JpaRepository<Question, UUID> {

    List<Question> findByLessonId(UUID lessonId);
}
