package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.CreateTrackRequest;
import com.icentric.Icentric.content.dto.TrackDetailResponse;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.service.TrackService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/content/tracks")
public class TrackController {

    private final TrackService service;

    public TrackController(TrackService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Track createTrack(
            @RequestBody CreateTrackRequest request
    ) {
        return service.createTrack(request);
    }
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<Track> getTracks() {
        return service.getAllTracks();
    }
    @GetMapping("/{trackId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public TrackDetailResponse getTrack(
            @PathVariable UUID trackId
    ) {
        return service.getTrack(trackId);
    }
}
