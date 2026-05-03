package com.icentric.Icentric.learning.service;


import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.LessonStepRepository;
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
import java.time.format.DateTimeFormatter;
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
    private final LessonStepRepository lessonStepRepository;
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
            LessonStepRepository lessonStepRepository,
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
        this.lessonStepRepository = lessonStepRepository;
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

        if (assignments.isEmpty()) {
            return List.of();
        }

        List<UUID> trackIds = assignments.stream()
                .map(UserAssignment::getTrackId)
                .distinct()
                .toList();

        Map<UUID, String> trackTitles = new HashMap<>();
        Map<UUID, Long> totalLessonsByTrack = new HashMap<>();
        Map<UUID, Long> completedLessonsByTrack = new HashMap<>();

        trackRepository.findAllById(trackIds)
                .forEach(track -> trackTitles.put(track.getId(), track.getTitle()));

        lessonRepository.countLessonsInTracks(trackIds)
                .forEach(row -> totalLessonsByTrack.put((UUID) row[0], (Long) row[1]));

        progressRepository.countCompletedLessonsByTrack(userId, trackIds)
                .forEach(row -> completedLessonsByTrack.put((UUID) row[0], (Long) row[1]));

        return assignments.stream().map(a -> {
            long completedLessons = completedLessonsByTrack.getOrDefault(a.getTrackId(), 0L);
            long totalLessons = totalLessonsByTrack.getOrDefault(a.getTrackId(), 0L);
            double completionPercent = totalLessons == 0 ? 0 : (completedLessons * 100.0) / totalLessons;

            return new LearnerAssignmentResponse(
                    a.getId(),
                    a.getTrackId(),
                    trackTitles.getOrDefault(a.getTrackId(), "Unknown Track"),
                    a.getAssignedAt(),
                    a.getDueDate(),
                    resolveAssignmentStatus(a),
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
                                lesson.getTitle()
                        );
                    }
                }
            }
        }

        return null;
    }

    @Transactional(readOnly = true)
    public List<LearningPathResponse> getLearningPaths(UUID userId) {
        tenantSchemaService.applyCurrentTenantSearchPath();

        List<UserAssignment> assignments = assignmentRepository.findByUserId(userId);
        List<LearningPathResponse> result = new ArrayList<>();

        DateTimeFormatter dateFormatter      = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);
        DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneOffset.UTC);

        for (UserAssignment assignment : assignments) {
            var track   = trackRepository.findById(assignment.getTrackId()).orElseThrow();
            var modules = moduleRepository.findByTrackIdOrderBySortOrder(track.getId());

            int completedModulesCount = 0;
            int totalModulesCount     = modules.size();
            int remainingMinsTotal    = 0;

            List<LearningPathResponse.ModuleItem>   moduleItems   = new ArrayList<>();
            List<LearningPathResponse.TimelineItem> timelineItems = new ArrayList<>();

            boolean previousModuleComplete = true;
            // FIX 1: use a 1-based counter so moduleNumber is always correct
            //         regardless of how sortOrder is stored in the DB
            int moduleIndex = 1;

            for (CourseModule module : modules) {
                var lessons = lessonRepository.findByModuleIdOrderBySortOrder(module.getId());

                int totalLessonsInModule     = lessons.size();
                int completedLessonsInModule = 0;
                List<LearningPathResponse.LessonItem> lessonItems = new ArrayList<>();

                // Collect topics and display items across all lessons in this module.
                // Universal rule:
                //   - Lesson HAS steps → expand steps as items; topics = step types (human-readable)
                //   - Lesson has NO steps  → lesson is the item; topics = prefix before ':' in title
                var topicSet = new java.util.LinkedHashSet<String>();
                int totalDisplayItems = 0;   // steps or lessons, whichever applies

                boolean previousLessonComplete = true;

                for (var lesson : lessons) {
                    boolean lessonDone = false;
                    java.util.List<UUID> completedStepIds = new java.util.ArrayList<>();
                    progressRepository.findByUserIdAndLessonId(userId, lesson.getId())
                            .ifPresent(p -> {
                                if (p.getCompletedStepIds() != null) {
                                    completedStepIds.addAll(p.getCompletedStepIds());
                                }
                            });
                    lessonDone = progressRepository.existsByUserIdAndLessonIdAndStatus(userId, lesson.getId(), "COMPLETED");

                    var steps = lessonStepRepository.findByLessonIdOrderBySortOrderAsc(lesson.getId());

                    if (!steps.isEmpty()) {
                        // ── NEW architecture: lesson wraps steps ──────────────────────────
                        steps.forEach(s -> topicSet.add(humanizeStepType(s.getStepType().name())));
                        totalDisplayItems += steps.size();

                        if (lessonDone || completedStepIds.size() >= steps.size()) {
                            // All steps show as completed
                            completedLessonsInModule += steps.size();
                            steps.forEach(s -> lessonItems.add(new LearningPathResponse.LessonItem(
                                    lesson.getId(), s.getTitle(), "COMPLETED", "Done", "Review"
                            )));
                        } else if (previousLessonComplete && previousModuleComplete) {
                            // Find the edge between completed steps and upcoming steps
                            boolean firstIncompleteFound = false;
                            for (var s : steps) {
                                if (completedStepIds.contains(s.getId())) {
                                    completedLessonsInModule++;
                                    lessonItems.add(new LearningPathResponse.LessonItem(
                                            lesson.getId(), s.getTitle(), "COMPLETED", "Done", "Review"
                                    ));
                                } else if (!firstIncompleteFound) {
                                    firstIncompleteFound = true;
                                    lessonItems.add(new LearningPathResponse.LessonItem(
                                            lesson.getId(), s.getTitle(), "IN_PROGRESS", "Next up", "Continue"
                                    ));
                                } else {
                                    lessonItems.add(new LearningPathResponse.LessonItem(
                                            lesson.getId(), s.getTitle(), "UPCOMING", "Upcoming", "Locked"
                                    ));
                                }
                            }
                            remainingMinsTotal += (lesson.getEstimatedMins() != null ? lesson.getEstimatedMins() : 0);
                        } else {
                            // Module locked — all steps locked
                            steps.forEach(s -> lessonItems.add(new LearningPathResponse.LessonItem(
                                    lesson.getId(), s.getTitle(), "UPCOMING", "Upcoming", "Locked"
                            )));
                            remainingMinsTotal += (lesson.getEstimatedMins() != null ? lesson.getEstimatedMins() : 0);
                        }
                        previousLessonComplete = lessonDone;
                    } else {
                        // ── OLD architecture: lesson is the atomic unit ───────────────────
                        String t = lesson.getTitle();
                        int colon = t.indexOf(':');
                        topicSet.add(colon >= 0 ? t.substring(0, colon).trim() : t);
                        totalDisplayItems++;

                        String lessonStatus;
                        if (lessonDone) {
                            lessonStatus = "COMPLETED";
                            completedLessonsInModule++;
                        } else if (previousLessonComplete && previousModuleComplete) {
                            lessonStatus = "IN_PROGRESS";
                            remainingMinsTotal += (lesson.getEstimatedMins() != null ? lesson.getEstimatedMins() : 0);
                        } else {
                            lessonStatus = "UPCOMING";
                            remainingMinsTotal += (lesson.getEstimatedMins() != null ? lesson.getEstimatedMins() : 0);
                        }

                        String meta        = lessonDone ? "Done" : (lessonStatus.equals("IN_PROGRESS") ? "Next up" : "Upcoming");
                        String actionLabel = lessonDone ? "Review" : (lessonStatus.equals("UPCOMING") ? "Locked" : "Continue");

                        lessonItems.add(new LearningPathResponse.LessonItem(
                                lesson.getId(), lesson.getTitle(), lessonStatus, meta, actionLabel
                        ));
                    }

                    previousLessonComplete = lessonDone;
                }

                List<String> topics = topicSet.isEmpty() ? List.of("General") : new ArrayList<>(topicSet);

                boolean moduleCompleted = (totalDisplayItems > 0
                        && completedLessonsInModule >= totalDisplayItems);
                if (moduleCompleted) completedModulesCount++;

                String moduleStatus;
                if (moduleCompleted) {
                    moduleStatus = "COMPLETED";
                } else if (previousModuleComplete) {
                    moduleStatus = "IN_PROGRESS";
                } else {
                    moduleStatus = "LOCKED";
                }

                int progressPercent = totalDisplayItems == 0
                        ? 0
                        : Math.min(100, (completedLessonsInModule * 100) / totalDisplayItems);

                String moduleMeta;
                if (moduleCompleted) {
                    moduleMeta = "Completed";
                } else if (moduleStatus.equals("LOCKED")) {
                    moduleMeta = "Unlocks after completing previous module";
                } else {
                    moduleMeta = completedLessonsInModule + " of " + totalLessonsInModule + " lessons done";
                }

                moduleItems.add(new LearningPathResponse.ModuleItem(
                        module.getId(),
                        moduleIndex,
                        module.getTitle(),
                        moduleStatus,
                        topics,
                        moduleMeta,
                        progressPercent,
                        lessonItems,
                        progressPercent,
                        completedLessonsInModule,
                        totalDisplayItems   // real count: steps if step-based, lessons otherwise
                ));

                // FIX 5: timeline label uses meaningful status text per module
                String timelineLabel;
                if (moduleCompleted) {
                    timelineLabel = "Completed";
                } else if (moduleStatus.equals("LOCKED")) {
                    timelineLabel = "Locked";
                } else {
                    timelineLabel = "In progress " + progressPercent + "%";
                }
                String timelineStatus = moduleCompleted ? "COMPLETE"
                        : (moduleStatus.equals("LOCKED") ? "LOCKED" : "CURRENT");

                timelineItems.add(new LearningPathResponse.TimelineItem(
                        module.getId(),
                        module.getTitle(),
                        timelineLabel,
                        timelineStatus
                ));

                previousModuleComplete = moduleCompleted;
                moduleIndex++;
            }

            String deadlineLabel     = assignment.getDueDate() != null
                    ? dateFormatter.format(assignment.getDueDate()) : "No deadline";
            Long daysLeft            = calculateDaysLeft(assignment);
            String daysRemainingLabel = daysLeft != null
                    ? (daysLeft >= 0 ? daysLeft + " days remaining" : Math.abs(daysLeft) + " days overdue")
                    : "";

            int estimatedMins = track.getEstimatedMins() != null ? track.getEstimatedMins() : 0;
            // FIX 4: actual computed remaining from incomplete lessons
            String remainingLabel = remainingMinsTotal > 0
                    ? "~" + remainingMinsTotal + " min remaining"
                    : "All done!";

            result.add(new LearningPathResponse(
                    track.getId(),
                    track.getTitle(),
                    "v" + track.getVersion(),
                    track.getDescription(),
                    true,
                    "~" + estimatedMins + " min total",
                    completedModulesCount,
                    totalModulesCount,
                    remainingLabel,            // FIX 4
                    deadlineLabel,
                    daysRemainingLabel,
                    timelineItems,
                    moduleItems
            ));
        }

        return result;
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
                .filter(a -> a.getDueDate() != null && resolveAssignmentStatus(a) != AssignmentStatus.COMPLETED)
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
            AssignmentStatus status = resolveAssignmentStatus(a);

            long completed = completedLessonsByTrack.getOrDefault(a.getTrackId(), 0L);

            long total = totalLessonsByTrack.getOrDefault(a.getTrackId(), 0L);

            int progress = total == 0 ? 0 :
                    (int) ((completed * 100) / total);

            Long daysLeft = calculateDaysLeft(a);

            trainings.add(new TrainingItem(
                    a.getTrackId(),
                    trackTitles.getOrDefault(a.getTrackId(), "Unknown Track"),
                    status,
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

        int modulesCompleted = 0;
        int totalModules = 0;
        if (!trackIds.isEmpty()) {
            int[] stats = calculateModuleStats(userId, trackIds);
            modulesCompleted = stats[0];
            totalModules = stats[1];
        }

        return new LearnerDashboardResponse(
                learnerName,
                learningStreakDays,
                modulesCompleted,
                totalModules,
                nextDeadline,
                trainings,
                certificates
        );
    }

    private int[] calculateModuleStats(UUID userId, List<UUID> trackIds) {
        int completedCount = 0;
        int totalCount = 0;
        for (UUID trackId : trackIds) {
            var modules = moduleRepository.findByTrackIdOrderBySortOrder(trackId);
            totalCount += modules.size();
            for (CourseModule module : modules) {
                var lessons = lessonRepository.findByModuleIdOrderBySortOrder(module.getId());
                if (lessons.isEmpty()) continue;

                int totalDisplayItems = 0;
                int completedDisplayItems = 0;

                for (var lesson : lessons) {
                    boolean lessonDone = progressRepository.existsByUserIdAndLessonIdAndStatus(userId, lesson.getId(), "COMPLETED");
                    var steps = lessonStepRepository.findByLessonIdOrderBySortOrderAsc(lesson.getId());

                    if (!steps.isEmpty()) {
                        totalDisplayItems += steps.size();
                        if (lessonDone) {
                            completedDisplayItems += steps.size();
                        } else {
                            var pOpt = progressRepository.findByUserIdAndLessonId(userId, lesson.getId());
                            if (pOpt.isPresent() && pOpt.get().getCompletedStepIds() != null) {
                                completedDisplayItems += pOpt.get().getCompletedStepIds().size();
                            }
                        }
                    } else {
                        totalDisplayItems++;
                        if (lessonDone) {
                            completedDisplayItems++;
                        }
                    }
                }

                if (totalDisplayItems > 0 && completedDisplayItems >= totalDisplayItems) {
                    completedCount++;
                }
            }
        }
        return new int[]{completedCount, totalCount};
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

    private AssignmentStatus resolveAssignmentStatus(UserAssignment assignment) {
        AssignmentStatus status = assignment.getStatus();
        if (status == AssignmentStatus.COMPLETED || status == AssignmentStatus.FAILED) {
            return status;
        }
        Instant dueDate = assignment.getDueDate();
        if (dueDate != null && dueDate.isBefore(Instant.now())) {
            return AssignmentStatus.OVERDUE;
        }
        return status == null ? AssignmentStatus.ASSIGNED : status;
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

    /**
     * Converts a StepType enum name to a human-readable topic label.
     * Used to populate the `topics` list in the learning-path response
     * when a lesson is step-based (new architecture).
     *
     * Examples: CONCEPT → "Concept", DO_DONT → "Do & Don't", QUIZ → "Quiz"
     */
    private String humanizeStepType(String stepTypeName) {
        return switch (stepTypeName) {
            case "CONCEPT"  -> "Concept";
            case "SCENARIO" -> "Scenario";
            case "QUIZ"     -> "Quiz";
            case "DO_DONT"  -> "Do & Don't";
            case "SUMMARY"  -> "Summary";
            case "HOOK"     -> "Hook";
            case "VIDEO"    -> "Video";
            default         -> stepTypeName.charAt(0) + stepTypeName.substring(1).toLowerCase().replace('_', ' ');
        };
    }
}
