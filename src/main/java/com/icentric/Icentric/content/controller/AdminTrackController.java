package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.service.TrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tenant-admin view for learning tracks.
 * Base: /api/v1/admin/content/tracks
 */
@RestController
@RequestMapping("/api/v1/admin/content/tracks")
@Tag(name = "Tracks (Tenant Admin)", description = "APIs for tenant admins to view available learning tracks")
public class AdminTrackController {

    private final TrackService service;

    public AdminTrackController(TrackService service) {
        this.service = service;
    }

    @Operation(summary = "List all published tracks", description = "Returns all published tracks available for assignment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Track list returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<Track> getPublishedTracks() {
        return service.getPublishedTracks();
    }
}
