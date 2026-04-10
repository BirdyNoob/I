package com.icentric.Icentric.learning.service;


import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.learning.dto.*;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LearnerDashboardService {

    private final UserAssignmentRepository assignmentRepository;
    private final LessonProgressRepository progressRepository;
    private final TrackRepository trackRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final IssuedCertificateRepository issuedCertificateRepository;
    private final CertificateRepository certificateRepository;
    private final TenantSchemaService tenantSchemaService;
    private final UserRepository userRepository;

    public LearnerDashboardService(
            UserAssignmentRepository assignmentRepository,
            LessonProgressRepository progressRepository,
            TrackRepository trackRepository,
            ModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            IssuedCertificateRepository issuedCertificateRepository,
            CertificateRepository certificateRepository,
            TenantSchemaService tenantSchemaService,
            UserRepository userRepository
    ) {
        this.assignmentRepository = assignmentRepository;
        this.progressRepository = progressRepository;
        this.trackRepository = trackRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.issuedCertificateRepository = issuedCertificateRepository;
        this.certificateRepository = certificateRepository;
        this.tenantSchemaService = tenantSchemaService;
        this.userRepository = userRepository;

    }

    @Transactional(readOnly = true)
    public List<LearnerAssignmentResponse> getAssignments(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

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
    @Transactional(readOnly = true)
    public NextLessonResponse getNextLesson(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

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
    @Transactional(readOnly = true)
    public LearnerDashboardResponse getDashboard(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        List<UserAssignment> assignments =
                assignmentRepository.findByUserId(userId);
        String learnerName = userRepository.findById(userId)
                .map(user -> user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail())
                .orElse("Learner");
        int learningStreakDays = calculateLearningStreak(userId);
        Instant nextDeadline = assignments.stream()
                .filter(a -> a.getDueDate() != null && a.getStatus() != AssignmentStatus.COMPLETED)
                .map(UserAssignment::getDueDate)
                .min(Comparator.naturalOrder())
                .orElse(null);

        List<UUID> trackIds = assignments.stream()
                .map(UserAssignment::getTrackId)
                .distinct()
                .toList();

        List<TrainingItem> trainings = new ArrayList<>();

        Map<UUID, String> trackTitles = new HashMap<>();
        Map<UUID, Long> totalLessonsByTrack = new HashMap<>();
        Map<UUID, Long> completedLessonsByTrack = new HashMap<>();

        if (!trackIds.isEmpty()) {
            trackRepository.findAllById(trackIds)
                    .forEach(track -> trackTitles.put(track.getId(), track.getTitle()));

            lessonRepository.countLessonsInTracks(trackIds)
                    .forEach(row -> totalLessonsByTrack.put((UUID) row[0], (Long) row[1]));

            progressRepository.countCompletedLessonsByTrack(userId, trackIds)
                    .forEach(row -> completedLessonsByTrack.put((UUID) row[0], (Long) row[1]));
        }

        for (UserAssignment a : assignments) {

            long completed = completedLessonsByTrack.getOrDefault(a.getTrackId(), 0L);

            long total = totalLessonsByTrack.getOrDefault(a.getTrackId(), 0L);

            int progress = total == 0 ? 0 :
                    (int) ((completed * 100) / total);

            Long daysLeft = calculateDaysLeft(a);

            trainings.add(new TrainingItem(
                    a.getTrackId(),
                    trackTitles.getOrDefault(a.getTrackId(), "Unknown Track"),
                    a.getStatus(),
                    a.getDueDate(),
                    daysLeft,
                    progress
            ));
        }

        trainings.sort(
                Comparator.comparingInt(this::priorityRank)
                        .thenComparing(
                                TrainingItem::daysLeft,
                                Comparator.nullsLast(Long::compareTo)
                        )
                        .thenComparing(TrainingItem::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TrainingItem::trackTitle, String.CASE_INSENSITIVE_ORDER)
        );

        // 🔥 Certificates
        List<CertificateItem> certificates =
                issuedCertificateRepository.findByUserId(userId)
                        .stream()
                        .map(c -> new CertificateItem(
                                c.getId(),
                                c.getTrackId(),
                                certificateRepository.findById(c.getCertificateId())
                                        .map(cert -> cert.getTitle())
                                        .orElse("Certificate"),
                                c.getIssuedAt(),
                                c.getStatus(),
                                c.getDownloadUrl()
                        ))
                        .toList();

        return new LearnerDashboardResponse(
                learnerName,
                learningStreakDays,
                nextDeadline,
                trainings,
                certificates
        );
    }

    @Transactional(readOnly = true)
    public TrackProgressResponse getTrackProgress(UUID userId, UUID trackId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        var track = trackRepository.findById(trackId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Track not found: " + trackId));

        var modules = moduleRepository.findByTrackIdOrderBySortOrder(trackId);

        int totalLessons = 0;
        int completedLessons = 0;
        var moduleProgressList = new ArrayList<TrackProgressResponse.ModuleProgress>();

        for (var module : modules) {
            var lessons = lessonRepository.findByModuleIdOrderBySortOrder(module.getId());
            var lessonStatuses = new java.util.ArrayList<TrackProgressResponse.LessonStatus>();

            boolean previousComplete = true; // first lesson is always unlocked
            int moduleCompleted = 0;

            for (var lesson : lessons) {
                boolean done = progressRepository.existsByUserIdAndLessonIdAndStatus(
                        userId, lesson.getId(), "COMPLETED");
                boolean locked = !previousComplete; // locked if the one before wasn't done

                lessonStatuses.add(new TrackProgressResponse.LessonStatus(
                        lesson.getId(),
                        lesson.getTitle(),
                        lesson.getLessonType(),
                        lesson.getSortOrder(),
                        done,
                        locked
                ));

                if (done) moduleCompleted++;
                previousComplete = done; // next lesson locks if this one isn't done
            }

            totalLessons += lessons.size();
            completedLessons += moduleCompleted;
            boolean moduleComplete = !lessons.isEmpty() && moduleCompleted == lessons.size();

            moduleProgressList.add(new TrackProgressResponse.ModuleProgress(
                    module.getId(),
                    module.getTitle(),
                    module.getSortOrder(),
                    moduleComplete,
                    lessonStatuses
            ));
        }

        int progressPercent = totalLessons == 0 ? 0 : (completedLessons * 100) / totalLessons;

        return new TrackProgressResponse(
                trackId,
                track.getTitle(),
                totalLessons,
                completedLessons,
                progressPercent,
                moduleProgressList
        );
    }

    private Long calculateDaysLeft(UserAssignment assignment) {
        if (assignment.getDueDate() == null) {
            return null;
        }

        long secondsLeft = assignment.getDueDate().getEpochSecond() - java.time.Instant.now().getEpochSecond();
        return Math.floorDiv(secondsLeft, 86_400);
    }

    private int priorityRank(TrainingItem item) {
        return switch (item.status()) {
            case OVERDUE -> 0;
            case IN_PROGRESS -> 1;
            case ASSIGNED -> 2;
            case FAILED -> 3;
            case COMPLETED -> 4;
            default -> 5;
        };
    }

    private int calculateLearningStreak(UUID userId) {
        List<Instant> completedAtList = progressRepository.findCompletedTimestampsByUserId(userId);
        if (completedAtList.isEmpty()) {
            return 0;
        }

        Set<LocalDate> completionDays = new LinkedHashSet<>();
        for (Instant completedAt : completedAtList) {
            completionDays.add(completedAt.atZone(ZoneOffset.UTC).toLocalDate());
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate cursor;
        if (completionDays.contains(today)) {
            cursor = today;
        } else if (completionDays.contains(today.minusDays(1))) {
            cursor = today.minusDays(1);
        } else {
            return 0;
        }

        int streak = 0;
        while (completionDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
