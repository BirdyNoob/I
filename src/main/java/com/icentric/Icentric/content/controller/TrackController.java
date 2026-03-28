package com.icentric.Icentric.content.controller;

import com.icentric.Icentric.content.dto.CreateTrackRequest;
import com.icentric.Icentric.content.dto.TrackDetailResponse;
import com.icentric.Icentric.content.dto.UpdateTrackRequest;
import com.icentric.Icentric.content.entity.Track;
import com.icentric.Icentric.content.service.TrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/content/tracks")
@Tag(name = "Tracks (Platform)", description = "APIs for platform admins to manage learning tracks")
public class TrackController {

    private final TrackService service;

    public TrackController(TrackService service) {
        this.service = service;
    }

    @Operation(summary = "Create a new track", description = "Creates a new learning track for the platform.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully created the track"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required")
    })
    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Track createTrack(
            @Valid @RequestBody CreateTrackRequest request
    ) {
        return service.createTrack(request);
    }

    @Operation(summary = "Get all tracks", description = "Retrieves a list of all tracks created on the platform.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of tracks"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - platform admin role required")
    })
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<Track> getTracks() {
        return service.getAllTracks();
    }

    @Operation(summary = "Get track details", description = "Retrieves complete details of a specific track by its UUID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved track details"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Track not found")
    })
    @GetMapping("/{trackId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public TrackDetailResponse getTrack(
            @Parameter(description = "UUID of the track") @PathVariable UUID trackId
    ) {
        return service.getTrack(trackId);
    }

    @Operation(summary = "Update a track", description = "Updates details of a specific track. Currently accessible without strict role check (verify access).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully updated the track"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Track not found")
    })
    @PutMapping("/tracks/{trackId}")
    public Track updateTrack(
            @Parameter(description = "UUID of the track to update") @PathVariable UUID trackId,
            @Valid @RequestBody UpdateTrackRequest request
    ) {
        return service.updateTrack(trackId, request);
    }

    @Operation(summary = "Publish a track", description = "Marks a specific track as published, making it available for assignment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully published the track"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Track not found")
    })
    @PatchMapping("/tracks/{id}/publish")
    public Track publishTrack(
            @Parameter(description = "UUID of the track to publish") @PathVariable UUID id
    ) {
        return service.publishTrack(id);
    }
}
