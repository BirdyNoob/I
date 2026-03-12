package com.icentric.Icentric.content.service;

import com.icentric.Icentric.content.dto.CreateTrackRequest;
import com.icentric.Icentric.content.dto.LessonResponse;
import com.icentric.Icentric.content.dto.ModuleResponse;
import com.icentric.Icentric.content.dto.TrackDetailResponse;
import com.icentric.Icentric.content.entity.CourseModule;
import com.icentric.Icentric.content.entity.Lesson;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.LessonRepository;
import com.icentric.Icentric.content.repository.ModuleRepository;
import com.icentric.Icentric.content.repository.TrackRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TrackService {

    private final TrackRepository repository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    public TrackService(TrackRepository repository, ModuleRepository moduleRepository, LessonRepository lessonRepository) {
        this.repository = repository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
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
}