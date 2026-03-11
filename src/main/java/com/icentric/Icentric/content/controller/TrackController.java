package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.CreateTrackRequest;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.service.TrackService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
