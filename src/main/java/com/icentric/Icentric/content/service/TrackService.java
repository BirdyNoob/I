package com.icentric.Icentric.content.service;

import com.icentric.Icentric.content.dto.*;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.repository.UserAssignmentRepository;
import com.icentric.Icentric.learning.service.RetrainingService;
import com.icentric.Icentric.audit.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TrackService {

    private final TrackRepository repository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final RetrainingService retrainingService;
    private final AuditService auditService;

    public TrackService(
            TrackRepository repository,
            ModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            UserAssignmentRepository assignmentRepository,
            RetrainingService retrainingService,
            AuditService auditService
    ) {
        this.repository = repository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.assignmentRepository = assignmentRepository;
        this.retrainingService = retrainingService;
        this.auditService = auditService;
    }

    // ── Admin: create track ────────────────────────────────────────────────────

    @Transactional
    public Track createTrack(CreateTrackRequest request) {
        Track track = new Track();
        track.setId(UUID.randomUUID());
        track.setSlug(request.slug());
        track.setTitle(request.title());
        track.setDescription(request.description());
        track.setDepartment(request.department());
        track.setTrackType(request.trackType());
        track.setEstimatedMins(request.estimatedMins());
        track.setVersion(1);
        track.setIsPublished(false);
        track.setStatus("DRAFT");
        track.setCreatedAt(Instant.now());
        return repository.save(track);
    }

    // ── Admin: list all tracks ─────────────────────────────────────────────────

    public List<Track> getAllTracks() {
        return repository.findAll();
    }

    // ── Admin / Learner: full track detail with ordered modules + lessons ──────

    @Transactional(readOnly = true)
    public TrackDetailResponse getTrack(UUID trackId) {
        Track track = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));

        // Modules ordered by sortOrder
        List<CourseModule> modules = moduleRepository.findByTrackIdOrderBySortOrder(trackId);

        List<ModuleResponse> moduleResponses = modules.stream()
                .map(module -> {
                    // Lessons ordered by sortOrder (enforces VIDEO → SCENARIO → DOS_DONTS → QUIZ sequence)
                    List<Lesson> lessons = lessonRepository.findByModuleIdOrderBySortOrder(module.getId());

                    List<LessonResponse> lessonResponses = lessons.stream()
                            .map(l -> new LessonResponse(
                                    l.getId(),
                                    l.getTitle(),
                                    l.getLessonType()))
                            .toList();

                    return new ModuleResponse(
                            module.getId(),
                            module.getTitle(),
                            lessonResponses
                    );
                })
                .toList();

        return new TrackDetailResponse(
                track.getId(),
                track.getTitle(),
                moduleResponses
        );
    }

    // ── Admin: update track (draft only) ───────────────────────────────────────

    @Transactional
    public Track updateTrack(UUID trackId, UpdateTrackRequest request) {
        Track track = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));

        if ("PUBLISHED".equals(track.getStatus())) {
            throw new IllegalStateException("Cannot edit a published track. Create a new version instead.");
        }

        if (request.title() != null)       track.setTitle(request.title());
        if (request.description() != null) track.setDescription(request.description());

        Track saved = repository.save(track);

        // Audit
        Object userIdRaw = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication().getDetails()
                : null;
        if (userIdRaw != null) {
            UUID adminId = UUID.fromString(userIdRaw.toString());
            auditService.log(adminId, "UPDATE_TRACK", "TRACK", trackId.toString(),
                    "Track fields updated");
        }

        // Trigger retraining for all existing assignees
        triggerRetrainingForTrack(trackId);

        return saved;
    }

    // ── Admin: publish track ───────────────────────────────────────────────────

    @Transactional
    public Track publishTrack(UUID trackId) {
        Track track = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));

        if ("PUBLISHED".equals(track.getStatus())) {
            return track; // idempotent
        }

        // Validate: must have at least one module with the full 4-lesson sequence
        validateTrackStructure(trackId);

        track.setStatus("PUBLISHED");
        track.setIsPublished(true);
        track.setVersion(track.getVersion() + 1);

        Track saved = repository.save(track);

        // Trigger retraining
        triggerRetrainingForTrack(trackId);

        return saved;
    }

    // ── Admin: unpublish / archive track ──────────────────────────────────────

    @Transactional
    public Track archiveTrack(UUID trackId) {
        Track track = repository.findById(trackId)
                .orElseThrow(() -> new NoSuchElementException("Track not found: " + trackId));
        track.setStatus("ARCHIVED");
        track.setIsPublished(false);
        return repository.save(track);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Ensures every module in the track has the mandatory lesson sequence:
     * VIDEO_CONCEPT → INTERACTIVE_SCENARIO → DOS_AND_DONTS → QUIZ.
     * Throws {@link IllegalStateException} if not satisfied.
     */
    private void validateTrackStructure(UUID trackId) {
        List<CourseModule> modules = moduleRepository.findByTrackIdOrderBySortOrder(trackId);
        if (modules.isEmpty()) {
            throw new IllegalStateException("Track must have at least one module before publishing.");
        }
        for (CourseModule module : modules) {
            List<Lesson> lessons = lessonRepository.findByModuleIdOrderBySortOrder(module.getId());
            long uniqueTypes = lessons.stream().map(Lesson::getLessonType).distinct().count();
            if (uniqueTypes < 4) {
                throw new IllegalStateException(
                        "Module '" + module.getTitle() + "' must have all 4 lesson types: " +
                        "VIDEO_CONCEPT, INTERACTIVE_SCENARIO, DOS_AND_DONTS, QUIZ.");
            }
        }
    }

    private void triggerRetrainingForTrack(UUID trackId) {
        List<UserAssignment> assignments = assignmentRepository.findByTrackId(trackId);
        for (UserAssignment assignment : assignments) {
            retrainingService.checkRetraining(assignment.getUserId(), trackId);
        }
    }
}
