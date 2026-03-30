package com.icentric.Icentric.learning.controller;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.AdminAssignmentSearchResponse;
import com.icentric.Icentric.learning.dto.BulkAssignmentRequest;
import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Validated
@Tag(name = "Assignments (Admin)", description = "APIs for managing assignments of tracks to users")
public class AssignmentController {

    private final AssignmentService service;

    public AssignmentController(
            AssignmentService service
    ) {
        this.service = service;
    }

    @Operation(summary = "Assign a track to a user", description = "Assigns a specific learning track to a user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Track assigned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/assignments")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public UserAssignment assignTrack(
            @Valid @RequestBody CreateAssignmentRequest request
    ) {
        return service.assignTrack(request);
    }

    @Operation(summary = "Bulk assign tracks", description = "Assigns learning tracks to multiple users in bulk.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk assignment completely/partially successful. Returns result map."),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/bulk")
    public Map<String, Object> bulkAssign(
            @Valid @RequestBody BulkAssignmentRequest request
    ) {
        return service.bulkAssign(request);
    }

    @Operation(summary = "Search assignments", description = "Search and filter assignments by status, track ID, or user ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved assignments"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/assignments/search")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Page<AdminAssignmentSearchResponse> searchAssignments(
            @Parameter(description = "Filter by assignment status") @RequestParam(required = false) AssignmentStatus status,
            @Parameter(description = "Filter by track ID") @RequestParam(required = false) UUID trackId,
            @Parameter(description = "Filter by user ID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "10") @Positive @Max(100) int size
    ) {

        return service.searchAssignments(
                status,
                trackId,
                userId,
                PageRequest.of(page, size, Sort.by("assignedAt").descending())
        );
    }
}
