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
import com.icentric.Icentric.learning.service.AssignmentService;
import com.icentric.Icentric.learning.service.RetrainingService;
import com.icentric.Icentric.audit.service.AuditService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TrackService {

    private final TrackRepository repository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final UserAssignmentRepository assignmentRepository;
    private final RetrainingService retrainingService;
    private final AuditService auditService;

    public TrackService(TrackRepository repository, ModuleRepository moduleRepository, LessonRepository lessonRepository, UserAssignmentRepository assignmentRepository, RetrainingService retrainingService, AuditService auditService) {
        this.repository = repository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.assignmentRepository = assignmentRepository;
        this.retrainingService = retrainingService;
        this.auditService = auditService;
    }

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
    public List<Track> getAllTracks() {
        return repository.findAll();
    }
    public TrackDetailResponse getTrack(UUID trackId) {

        Track track = repository.findById(trackId)
                .orElseThrow();

        List<CourseModule> modules = moduleRepository.findByTrackId(trackId);

        List<ModuleResponse> moduleResponses = modules.stream()
                .map(module -> {

                    List<Lesson> lessons =
                            lessonRepository.findByModuleId(module.getId());

                    List<LessonResponse> lessonResponses =
                            lessons.stream()
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

                }).toList();

        return new TrackDetailResponse(
                track.getId(),
                track.getTitle(),
                moduleResponses
        );
    }
    public Track updateTrack(UUID trackId, UpdateTrackRequest request) {

        var track = repository.findById(trackId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Track not found"));

        if ("PUBLISHED".equals(track.getStatus())) {
            throw new IllegalStateException("Cannot edit published track. Create new version.");
        }
        // update fields
        if (request.title() != null) {
            track.setTitle(request.title());
        }

        if (request.description() != null) {
            track.setDescription(request.description());
        }

        Track saved = repository.save(track);

        Object userIdRaw = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getDetails() : null;
        UUID adminUserId = userIdRaw != null ? (userIdRaw instanceof String ? UUID.fromString((String) userIdRaw) : UUID.fromString(userIdRaw.toString())) : null;
        if (adminUserId != null) {
            auditService.log(adminUserId, "UPDATE_TRACK", "TRACK", trackId.toString(), "Track updated and version incremented");
        }

        // 🔥 trigger retraining
        List<UserAssignment> assignments =
                assignmentRepository.findByTrackId(trackId);

        for (var assignment : assignments) {

            retrainingService.checkRetraining(
                    assignment.getUserId(),
                    trackId
            );
        }

        return saved;
    }
    public Track publishTrack(UUID trackId) {

        var track = repository.findById(trackId)
                .orElseThrow();

        if ("PUBLISHED".equals(track.getStatus())) {
            return track; // already published
        }

        // 🔥 mark published
        track.setStatus("PUBLISHED");

        // 🔥 increment version
        track.setVersion(track.getVersion() + 1);

        Track saved = repository.save(track);

        // 🔥 trigger retraining
        List<UserAssignment> assignments =
                assignmentRepository.findByTrackId(trackId);

        for (var assignment : assignments) {
            retrainingService.checkRetraining(
                    assignment.getUserId(),
                    trackId
            );
        }

        return saved;
    }
}
