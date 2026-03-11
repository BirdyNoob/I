package com.icentric.Icentric.content.service;

import com.icentric.Icentric.content.dto.CreateTrackRequest;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.repository.TrackRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TrackService {

    private final TrackRepository repository;

    public TrackService(TrackRepository repository) {
        this.repository = repository;
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
}