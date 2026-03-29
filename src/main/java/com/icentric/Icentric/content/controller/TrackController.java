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

/**
 * Platform-admin CRUD for learning tracks.
 * Base: /api/v1/platform/content/tracks
 */
@RestController
@RequestMapping("/api/v1/platform/content/tracks")
@Tag(name = "Tracks (Platform Admin)", description = "APIs for platform admins to manage learning tracks")
public class TrackController {

    private final TrackService service;

    public TrackController(TrackService service) {
        this.service = service;
    }

    // ── POST /tracks ───────────────────────────────────────────────────────────

    @Operation(summary = "Create a track", description = "Creates a new learning track in DRAFT status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Track created"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden — platform admin role required")
    })
    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Track createTrack(
            @Valid @RequestBody CreateTrackRequest request
    ) {
        return service.createTrack(request);
    }

    // ── GET /tracks ────────────────────────────────────────────────────────────

    @Operation(summary = "List all tracks", description = "Returns all tracks (all statuses).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Track list returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<Track> getTracks() {
        return service.getAllTracks();
    }

    // ── GET /tracks/{trackId} ──────────────────────────────────────────────────

    @Operation(
            summary = "Get track detail",
            description = "Returns the track with its modules and lessons (ordered by sortOrder). " +
                          "Each lesson carries its lessonType so the frontend knows what component to render.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Track detail returned"),
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

    // ── PUT /tracks/{trackId} ──────────────────────────────────────────────────

    @Operation(
            summary = "Update a track",
            description = "Updates title and/or description. Only allowed while track is in DRAFT status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Track updated"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Track not found"),
            @ApiResponse(responseCode = "409", description = "Track already published — cannot edit")
    })
    @PutMapping("/{trackId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Track updateTrack(
            @Parameter(description = "UUID of the track") @PathVariable UUID trackId,
            @Valid @RequestBody UpdateTrackRequest request
    ) {
        return service.updateTrack(trackId, request);
    }

    // ── PATCH /tracks/{trackId}/publish ───────────────────────────────────────

    @Operation(
            summary = "Publish a track",
            description = "Validates that every module has all 4 lesson types, then marks the track PUBLISHED " +
                          "and increments its version. Published tracks trigger retraining for existing assignees.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Track published"),
            @ApiResponse(responseCode = "400", description = "Track structure incomplete"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Track not found")
    })
    @PatchMapping("/{trackId}/publish")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Track publishTrack(
            @Parameter(description = "UUID of the track") @PathVariable UUID trackId
    ) {
        return service.publishTrack(trackId);
    }

    // ── PATCH /tracks/{trackId}/archive ───────────────────────────────────────

    @Operation(
            summary = "Archive a track",
            description = "Sets track status to ARCHIVED and un-publishes it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Track archived"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Track not found")
    })
    @PatchMapping("/{trackId}/archive")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Track archiveTrack(
            @Parameter(description = "UUID of the track") @PathVariable UUID trackId
    ) {
        return service.archiveTrack(trackId);
    }
}
