package com.icentric.Icentric.learning.service;


import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.LessonStepRepository;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.entity.LessonStep;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.learning.dto.*;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.entity.LessonProgress;
import com.icentric.Icentric.learning.entity.Certificate;
import com.icentric.Icentric.learning.entity.IssuedCertificate;
import com.icentric.Icentric.learning.repository.CertificateRepository;
import com.icentric.Icentric.learning.repository.IssuedCertificateRepository;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.learning.repository.LessonProgressRepository;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.identity.repository.UserRepository;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.entity.ModuleProgress;
import com.icentric.Icentric.learning.repository.ModuleProgressRepository;
import com.icentric.Icentric.tenant.TenantSchemaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Collectors;
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
    private final ModuleProgressRepository moduleProgressRepository;

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
            UserRepository userRepository,
            ModuleProgressRepository moduleProgressRepository
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
        this.moduleProgressRepository = moduleProgressRepository;
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

        List<Track> validTracks = trackRepository.findAllById(trackIds)
                .stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsPublished()))
                .toList();
        
        Set<UUID> validTrackIds = validTracks.stream().map(com.icentric.Icentric.content.entity.Track::getId).collect(java.util.stream.Collectors.toSet());

        validTracks.forEach(track -> trackTitles.put(track.getId(), track.getTitle()));

        lessonRepository.countLessonsInTracks(new java.util.ArrayList<>(validTrackIds))
                .forEach(row -> totalLessonsByTrack.put((UUID) row[0], (Long) row[1]));

        progressRepository.countCompletedLessonsByTrack(userId, new java.util.ArrayList<>(validTrackIds))
                .forEach(row -> completedLessonsByTrack.put((UUID) row[0], (Long) row[1]));

        return assignments.stream()
                .filter(a -> validTrackIds.contains(a.getTrackId()))
                .map(a -> {
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

        List<UserAssignment> assignments = assignmentRepository.findByUserId(userId);
        if (assignments.isEmpty()) {
            return null;
        }

        List<UUID> trackIds = assignments.stream().map(UserAssignment::getTrackId).toList();
        List<Track> tracks = trackRepository.findAllById(trackIds);
        if (tracks.isEmpty()) {
            tracks = new ArrayList<>();
            for (UUID trackId : trackIds) {
                trackRepository.findById(trackId).ifPresent(tracks::add);
            }
        }
        Map<UUID, Track> trackMap = tracks.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsPublished()))
                .collect(Collectors.toMap(Track::getId, t -> t, (a, b) -> a));

        List<CourseModule> allModules = moduleRepository.findByTrackIdIn(trackIds);
        if (allModules.isEmpty()) {
            allModules = new ArrayList<>();
            for (UUID trackId : trackIds) {
                allModules.addAll(moduleRepository.findByTrackIdOrderBySortOrder(trackId));
            }
        }
        Map<UUID, List<CourseModule>> modulesByTrackId = allModules.stream()
                .collect(Collectors.groupingBy(CourseModule::getTrackId));

        List<UUID> moduleIds = allModules.stream().map(CourseModule::getId).toList();
        List<Lesson> allLessons = moduleIds.isEmpty() ? List.of() : lessonRepository.findByModuleIdIn(moduleIds);
        if (allLessons.isEmpty() && !moduleIds.isEmpty()) {
            allLessons = new ArrayList<>();
            for (UUID moduleId : moduleIds) {
                allLessons.addAll(lessonRepository.findByModuleIdOrderBySortOrder(moduleId));
            }
        }
        Map<UUID, List<Lesson>> lessonsByModuleId = allLessons.stream()
                .collect(Collectors.groupingBy(Lesson::getModuleId));

        List<UUID> lessonIds = allLessons.stream().map(Lesson::getId).toList();
        List<LessonProgress> progressList = (lessonIds.isEmpty()) 
                ? List.of() 
                : progressRepository.findByUserIdAndLessonIdIn(userId, lessonIds);
        if (progressList.isEmpty() && !lessonIds.isEmpty()) {
            progressList = new ArrayList<>();
            for (UUID lessonId : lessonIds) {
                progressRepository.findByUserIdAndLessonId(userId, lessonId).ifPresent(progressList::add);
            }
        }
        Map<UUID, LessonProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, p -> p, (a, b) -> a));

        for (var assignment : assignments) {
            Track track = trackMap.get(assignment.getTrackId());
            if (track == null) {
                continue;
            }

            List<CourseModule> modules = modulesByTrackId.getOrDefault(track.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(CourseModule::getSortOrder))
                    .toList();

            for (CourseModule module : modules) {
                List<Lesson> lessons = lessonsByModuleId.getOrDefault(module.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(l -> l.getSortOrder() != null ? l.getSortOrder() : 0))
                        .toList();

                for (var lesson : lessons) {
                    LessonProgress progress = progressMap.get(lesson.getId());
                    boolean completed = progress != null && "COMPLETED".equals(progress.getStatus());
                    if (!completed) {
                        completed = progressRepository.existsByUserIdAndLessonIdAndStatus(userId, lesson.getId(), "COMPLETED");
                    }

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
        if (assignments.isEmpty()) {
            return List.of();
        }

        List<UUID> trackIds = assignments.stream().map(UserAssignment::getTrackId).toList();
        List<Track> tracks = trackRepository.findAllById(trackIds);
        if (tracks.isEmpty()) {
            tracks = new ArrayList<>();
            for (UUID trackId : trackIds) {
                trackRepository.findById(trackId).ifPresent(tracks::add);
            }
        }
        Map<UUID, Track> trackMap = tracks.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsPublished()))
                .collect(Collectors.toMap(Track::getId, t -> t, (a, b) -> a));

        List<CourseModule> allModules = moduleRepository.findByTrackIdIn(trackIds);
        if (allModules.isEmpty()) {
            allModules = new ArrayList<>();
            for (UUID trackId : trackIds) {
                allModules.addAll(moduleRepository.findByTrackIdOrderBySortOrder(trackId));
            }
        }
        Map<UUID, List<CourseModule>> modulesByTrackId = allModules.stream()
                .collect(Collectors.groupingBy(CourseModule::getTrackId));

        List<UUID> moduleIds = allModules.stream().map(CourseModule::getId).toList();
        List<Lesson> allLessons = moduleIds.isEmpty() ? List.of() : lessonRepository.findByModuleIdIn(moduleIds);
        if (allLessons.isEmpty() && !moduleIds.isEmpty()) {
            allLessons = new ArrayList<>();
            for (UUID moduleId : moduleIds) {
                allLessons.addAll(lessonRepository.findByModuleIdOrderBySortOrder(moduleId));
            }
        }
        Map<UUID, List<Lesson>> lessonsByModuleId = allLessons.stream()
                .collect(Collectors.groupingBy(Lesson::getModuleId));

        List<UUID> lessonIds = allLessons.stream().map(Lesson::getId).toList();
        List<LessonStep> allSteps = lessonIds.isEmpty() ? List.of() : lessonStepRepository.findByLessonIdIn(lessonIds);
        if (allSteps.isEmpty() && !lessonIds.isEmpty()) {
            allSteps = new ArrayList<>();
            for (UUID lessonId : lessonIds) {
                allSteps.addAll(lessonStepRepository.findByLessonIdOrderBySortOrderAsc(lessonId));
            }
        }
        Map<UUID, List<LessonStep>> stepsByLessonId = allSteps.stream()
                .collect(Collectors.groupingBy(LessonStep::getLessonId));

        List<LessonProgress> progressList = (lessonIds.isEmpty()) 
                ? List.of() 
                : progressRepository.findByUserIdAndLessonIdIn(userId, lessonIds);
        if (progressList.isEmpty() && !lessonIds.isEmpty()) {
            progressList = new ArrayList<>();
            for (UUID lessonId : lessonIds) {
                progressRepository.findByUserIdAndLessonId(userId, lessonId).ifPresent(progressList::add);
            }
        }
        Map<UUID, LessonProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, p -> p, (a, b) -> a));

        List<LearningPathResponse> result = new ArrayList<>();

        List<ModuleProgress> moduleProgressList = moduleProgressRepository.findByUserId(userId);
        Map<UUID, ModuleProgress> moduleProgressMap = new HashMap<>();
        for (ModuleProgress mp : moduleProgressList) {
            moduleProgressMap.put(mp.getModuleId(), mp);
        }

        DateTimeFormatter dateFormatter      = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);
        DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneOffset.UTC);

        for (UserAssignment assignment : assignments) {
            Track track = trackMap.get(assignment.getTrackId());
            if (track == null) {
                continue;
            }
            List<CourseModule> modules = modulesByTrackId.getOrDefault(track.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(CourseModule::getSortOrder))
                    .toList();

            int completedModulesCount = 0;
            int totalModulesCount     = modules.size();
            int remainingMinsTotal    = 0;

            List<LearningPathResponse.ModuleItem>   moduleItems   = new ArrayList<>();
            List<LearningPathResponse.TimelineItem> timelineItems = new ArrayList<>();

            boolean previousModuleComplete = true;
            int moduleIndex = 1;

            for (CourseModule module : modules) {
                List<Lesson> lessons = lessonsByModuleId.getOrDefault(module.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(l -> l.getSortOrder() != null ? l.getSortOrder() : 0))
                        .toList();

                int totalLessonsInModule     = lessons.size();
                int completedLessonsInModule = 0;
                List<LearningPathResponse.LessonItem> lessonItems = new ArrayList<>();

                var topicSet = new java.util.LinkedHashSet<String>();
                int totalDisplayItems = 0;   // steps or lessons, whichever applies

                boolean previousLessonComplete = true;

                for (var lesson : lessons) {
                    LessonProgress p = progressMap.get(lesson.getId());
                    boolean lessonDone = p != null && "COMPLETED".equals(p.getStatus());
                    if (!lessonDone) {
                        lessonDone = progressRepository.existsByUserIdAndLessonIdAndStatus(userId, lesson.getId(), "COMPLETED");
                    }
                    java.util.List<UUID> completedStepIds = new java.util.ArrayList<>();
                    if (p != null && p.getCompletedStepIds() != null) {
                        completedStepIds.addAll(p.getCompletedStepIds());
                    }

                    List<LessonStep> steps = stepsByLessonId.getOrDefault(lesson.getId(), List.of()).stream()
                            .sorted(Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0))
                            .toList();

                    if (!steps.isEmpty()) {
                        steps.forEach(s -> topicSet.add(humanizeStepType(s.getStepType().name())));
                        totalDisplayItems += steps.size();

                        if (lessonDone || completedStepIds.size() >= steps.size()) {
                            completedLessonsInModule += steps.size();
                            steps.forEach(s -> lessonItems.add(new LearningPathResponse.LessonItem(
                                    lesson.getId(), s.getTitle(), "COMPLETED", "Done", "Review"
                            )));
                        } else if (previousLessonComplete && previousModuleComplete) {
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
                            steps.forEach(s -> lessonItems.add(new LearningPathResponse.LessonItem(
                                    lesson.getId(), s.getTitle(), "UPCOMING", "Upcoming", "Locked"
                            )));
                            remainingMinsTotal += (lesson.getEstimatedMins() != null ? lesson.getEstimatedMins() : 0);
                        }
                    } else {
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
                    var mp = moduleProgressMap.get(module.getId());
                    if (mp != null) {
                        moduleStatus = mp.getStatus();
                    } else {
                        moduleStatus = (completedLessonsInModule > 0) ? "IN_PROGRESS" : "NOT_STARTED";
                    }
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
                } else if (moduleStatus.equals("NOT_STARTED")) {
                    moduleMeta = "Not started";
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
                        totalDisplayItems
                ));

                String timelineLabel;
                if (moduleCompleted) {
                    timelineLabel = "Completed";
                } else if (moduleStatus.equals("LOCKED")) {
                    timelineLabel = "Locked";
                } else if (moduleStatus.equals("NOT_STARTED")) {
                    timelineLabel = "Not started";
                } else {
                    timelineLabel = "In progress " + progressPercent + "%";
                }
                String timelineStatus = moduleCompleted ? "COMPLETE"
                        : (moduleStatus.equals("LOCKED") ? "LOCKED"
                        : (moduleStatus.equals("NOT_STARTED") ? "NOT_STARTED" : "CURRENT"));

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
                    remainingLabel,
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

        if (!trackIds.isEmpty()) {
            trackRepository.findAllById(trackIds).stream()
                    .filter(t -> Boolean.TRUE.equals(t.getIsPublished()))
                    .forEach(track -> trackTitles.put(track.getId(), track.getTitle()));
        }

        // 🔥 Pre-fetch progress hierarchy data
        List<CourseModule> allModules = trackIds.isEmpty() ? List.of() : moduleRepository.findByTrackIdIn(trackIds);
        Map<UUID, List<CourseModule>> modulesByTrackId = allModules.stream()
                .collect(Collectors.groupingBy(CourseModule::getTrackId));

        List<UUID> moduleIds = allModules.stream().map(CourseModule::getId).toList();
        List<Lesson> allLessons = moduleIds.isEmpty() ? List.of() : lessonRepository.findByModuleIdIn(moduleIds);
        Map<UUID, List<Lesson>> lessonsByModuleId = allLessons.stream()
                .collect(Collectors.groupingBy(Lesson::getModuleId));

        List<UUID> lessonIds = allLessons.stream().map(Lesson::getId).toList();
        List<LessonStep> allSteps = lessonIds.isEmpty() ? List.of() : lessonStepRepository.findByLessonIdIn(lessonIds);
        Map<UUID, List<LessonStep>> stepsByLessonId = allSteps.stream()
                .collect(Collectors.groupingBy(LessonStep::getLessonId));

        List<LessonProgress> progressList = (lessonIds.isEmpty()) 
                ? List.of() 
                : progressRepository.findByUserIdAndLessonIdIn(userId, lessonIds);
        Map<UUID, LessonProgress> progressByLessonId = progressList.stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, p -> p, (a, b) -> a));

        for (UserAssignment a : assignments) {
            if (!trackTitles.containsKey(a.getTrackId())) {
                continue;
            }
            
            AssignmentStatus status = resolveAssignmentStatus(a);

            // Calculate step-aware progress in memory
            int progress = calculateTrackProgressPercent(
                    a.getTrackId(),
                    modulesByTrackId,
                    lessonsByModuleId,
                    stepsByLessonId,
                    progressByLessonId
            );

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

        // 🔥 Certificates — Pre-fetch titles in a single query
        List<IssuedCertificate> issuedCerts = issuedCertificateRepository.findByUserId(userId);
        List<UUID> certificateIds = issuedCerts.stream().map(IssuedCertificate::getCertificateId).distinct().toList();
        Map<UUID, String> certificateTitles = certificateIds.isEmpty() ? Map.of() : certificateRepository.findAllById(certificateIds).stream()
                .collect(Collectors.toMap(Certificate::getId, Certificate::getTitle, (a, b) -> a));

        List<CertificateItem> certificates = issuedCerts.stream()
                .map(c -> new CertificateItem(
                        c.getId(),
                        c.getTrackId(),
                        certificateTitles.getOrDefault(c.getCertificateId(), "Certificate"),
                        c.getIssuedAt(),
                        c.getStatus(),
                        c.getDownloadUrl()
                ))
                .toList();

        int modulesCompleted = 0;
        int totalModules = 0;
        if (!trackIds.isEmpty()) {
            int[] stats = calculateModuleStats(
                    trackIds,
                    modulesByTrackId,
                    lessonsByModuleId,
                    stepsByLessonId,
                    progressByLessonId
            );
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

    private int[] calculateModuleStats(
            List<UUID> trackIds,
            Map<UUID, List<CourseModule>> modulesByTrackId,
            Map<UUID, List<Lesson>> lessonsByModuleId,
            Map<UUID, List<LessonStep>> stepsByLessonId,
            Map<UUID, LessonProgress> progressByLessonId
    ) {
        int completedCount = 0;
        int totalCount = 0;
        for (UUID trackId : trackIds) {
            List<CourseModule> modules = modulesByTrackId.getOrDefault(trackId, List.of()).stream()
                    .sorted(Comparator.comparingInt(CourseModule::getSortOrder))
                    .toList();
            totalCount += modules.size();
            for (CourseModule module : modules) {
                List<Lesson> lessons = lessonsByModuleId.getOrDefault(module.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(l -> l.getSortOrder() != null ? l.getSortOrder() : 0))
                        .toList();
                if (lessons.isEmpty()) continue;

                int totalDisplayItems = 0;
                int completedDisplayItems = 0;

                for (var lesson : lessons) {
                    LessonProgress progress = progressByLessonId.get(lesson.getId());
                    boolean lessonDone = progress != null && "COMPLETED".equals(progress.getStatus());
                    List<LessonStep> steps = stepsByLessonId.getOrDefault(lesson.getId(), List.of()).stream()
                            .sorted(Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0))
                            .toList();

                    if (!steps.isEmpty()) {
                        totalDisplayItems += steps.size();
                        if (lessonDone) {
                            completedDisplayItems += steps.size();
                        } else {
                            if (progress != null && progress.getCompletedStepIds() != null) {
                                completedDisplayItems += progress.getCompletedStepIds().size();
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
        List<UUID> moduleIds = modules.stream().map(CourseModule::getId).toList();

        List<Lesson> allLessons = moduleIds.isEmpty() ? List.of() : lessonRepository.findByModuleIdIn(moduleIds);
        Map<UUID, List<Lesson>> lessonsByModuleId = allLessons.stream()
                .collect(Collectors.groupingBy(Lesson::getModuleId));

        List<UUID> lessonIds = allLessons.stream().map(Lesson::getId).toList();
        List<LessonProgress> progressList = (lessonIds.isEmpty()) 
                ? List.of() 
                : progressRepository.findByUserIdAndLessonIdIn(userId, lessonIds);
        Map<UUID, LessonProgress> progressMap = progressList.stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, p -> p, (a, b) -> a));

        int totalLessons = 0;
        int completedLessons = 0;
        var moduleProgressList = new ArrayList<TrackProgressResponse.ModuleProgress>();

        for (var module : modules) {
            List<Lesson> lessons = lessonsByModuleId.getOrDefault(module.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(l -> l.getSortOrder() != null ? l.getSortOrder() : 0))
                    .toList();
            var lessonStatuses = new java.util.ArrayList<TrackProgressResponse.LessonStatus>();

            boolean previousComplete = true; // first lesson is always unlocked
            int moduleCompleted = 0;

            for (var lesson : lessons) {
                LessonProgress progress = progressMap.get(lesson.getId());
                boolean done = progress != null && "COMPLETED".equals(progress.getStatus());
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

    private int calculateTrackProgressPercent(
            UUID trackId,
            Map<UUID, List<CourseModule>> modulesByTrackId,
            Map<UUID, List<Lesson>> lessonsByModuleId,
            Map<UUID, List<LessonStep>> stepsByLessonId,
            Map<UUID, LessonProgress> progressByLessonId
    ) {
        List<CourseModule> modules = modulesByTrackId.getOrDefault(trackId, List.of()).stream()
                .sorted(Comparator.comparingInt(CourseModule::getSortOrder))
                .toList();
        long totalDisplayItems = 0;
        long completedDisplayItems = 0;

        for (var module : modules) {
            List<Lesson> lessons = lessonsByModuleId.getOrDefault(module.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(l -> l.getSortOrder() != null ? l.getSortOrder() : 0))
                    .toList();
            for (var lesson : lessons) {
                List<LessonStep> steps = stepsByLessonId.getOrDefault(lesson.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0))
                        .toList();

                if (!steps.isEmpty()) {
                    totalDisplayItems += steps.size();
                    LessonProgress progress = progressByLessonId.get(lesson.getId());
                    if (progress != null && progress.getCompletedStepIds() != null) {
                        completedDisplayItems += progress.getCompletedStepIds().size();
                    }
                    if (progress != null && "COMPLETED".equals(progress.getStatus())) {
                        completedDisplayItems = Math.max(completedDisplayItems, (long) steps.size());
                    }
                } else {
                    totalDisplayItems++;
                    LessonProgress progress = progressByLessonId.get(lesson.getId());
                    if (progress != null && "COMPLETED".equals(progress.getStatus())) {
                        completedDisplayItems++;
                    }
                }
            }
        }

        return totalDisplayItems == 0 ? 0 : (int) ((completedDisplayItems * 100) / totalDisplayItems);
    }
}
