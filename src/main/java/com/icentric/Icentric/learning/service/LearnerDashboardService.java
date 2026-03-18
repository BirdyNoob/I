package com.icentric.Icentric.learning.service;


import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.learning.dto.LearnerAssignmentResponse;
import com.icentric.Icentric.learning.dto.NextLessonResponse;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.content.repository.TrackRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LearnerDashboardService {

    private final UserAssignmentRepository assignmentRepository;
    private final LessonProgressRepository progressRepository;
    private final TrackRepository trackRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    public LearnerDashboardService(
            UserAssignmentRepository assignmentRepository,
            LessonProgressRepository progressRepository,
            TrackRepository trackRepository,
            ModuleRepository moduleRepository,
            LessonRepository lessonRepository
    ) {
        this.assignmentRepository = assignmentRepository;
        this.progressRepository = progressRepository;
        this.trackRepository = trackRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;

    }

    public List<LearnerAssignmentResponse> getAssignments(UUID userId) {

        List<UserAssignment> assignments =
                assignmentRepository.findByUserId(userId);

        return assignments.stream().map(a -> {

            var track = trackRepository.findById(a.getTrackId()).orElseThrow();

            long completedLessons =
                    progressRepository.countByUserIdAndStatus(userId, "COMPLETED");

            long totalLessons =
                    progressRepository.countByUserId(userId);

            double completionPercent =
                    totalLessons == 0 ? 0 :
                            (completedLessons * 100.0) / totalLessons;

            return new LearnerAssignmentResponse(
                    a.getId(),
                    a.getTrackId(),
                    track.getTitle(),
                    a.getAssignedAt(),
                    a.getDueDate(),
                    a.getStatus(),
                    (int) totalLessons,
                    (int) completedLessons,
                    completionPercent
            );

        }).toList();
    }
    public NextLessonResponse getNextLesson(UUID userId) {

        var assignments = assignmentRepository.findByUserId(userId);

        for (var assignment : assignments) {

            var track = trackRepository
                    .findById(assignment.getTrackId())
                    .orElseThrow();

            var modules = moduleRepository
                    .findByTrackIdOrderBySortOrder(track.getId());

            for (CourseModule module : modules) {

                var lessons = lessonRepository
                        .findByModuleIdOrderBySortOrder(module.getId());

                for (var lesson : lessons) {

                    boolean completed =
                            progressRepository.existsByUserIdAndLessonIdAndStatus(
                                    userId,
                                    lesson.getId(),
                                    "COMPLETED"
                            );

                    if (!completed) {

                        return new NextLessonResponse(
                                track.getId(),
                                module.getId(),
                                lesson.getId(),
                                track.getTitle(),
                                module.getTitle(),
                                lesson.getTitle(),
                                lesson.getLessonType()
                        );
                    }
                }
            }
        }

        return null;
    }
}
