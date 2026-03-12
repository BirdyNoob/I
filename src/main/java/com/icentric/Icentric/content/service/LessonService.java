package com.icentric.Icentric.content.service;
import com.icentric.Icentric.content.dto.CreateLessonRequest;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.repository.LessonRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class LessonService {

    private final LessonRepository repository;

    public LessonService(LessonRepository repository) {
        this.repository = repository;
    }

    public Lesson createLesson(UUID moduleId, CreateLessonRequest request) {

        Lesson lesson = new Lesson();

        lesson.setId(UUID.randomUUID());
        lesson.setModuleId(moduleId);
        lesson.setTitle(request.title());
        lesson.setLessonType(request.lessonType());
        lesson.setContentJson(request.contentJson());
        lesson.setVideoUrl(request.videoUrl());
        lesson.setResourceUrl(request.resourceUrl());
        lesson.setSortOrder(request.sortOrder());
        lesson.setIsPublished(false);
        lesson.setCreatedAt(Instant.now());

        return repository.save(lesson);
    }
}
