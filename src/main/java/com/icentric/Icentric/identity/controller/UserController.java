package com.icentric.Icentric.identity.controller;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.identity.dto.BulkUploadResponse;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.dto.UserDetailResponse;
import com.icentric.Icentric.identity.dto.UserResponse;
import com.icentric.Icentric.identity.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
@Validated
@Tag(name = "User Management", description = "APIs for managing users within a tenant")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @Operation(summary = "Get all users", description = "Retrieve a paginated list of users within the tenant. Supports optional filtering by department, role, and active status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of users"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - user does not have admin privileges")
    })
    @GetMapping("/users")
    public Page<UserResponse> getUsers(
            @Parameter(description = "Filter by department name") @RequestParam(required = false) Department department,
            @Parameter(description = "Filter by user role") @RequestParam(required = false) String role,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "10") @Positive @Max(100) int size
    ) {
        return service.getUsers(department, role, isActive, PageRequest.of(page, size));
    }

    @Operation(
            summary = "Get user detail",
            description = "Returns full profile, learning progress, assigned tracks, and certificate count for a specific user. SUPER_ADMIN accounts are excluded."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User detail retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found in this tenant"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/users/{id}/detail")
    public ResponseEntity<UserDetailResponse> getUserDetail(
            @Parameter(description = "UUID of the user") @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.getUserDetail(id));
    }

    @Operation(summary = "Create a new user", description = "Creates a new user within the current tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/users/create")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return service.createUser(request);
    }

    @Operation(summary = "Update an existing user", description = "Updates details of an existing user specified by their UUID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/users/{id}")
    public UserResponse updateUser(
            @Parameter(description = "UUID of the user to update") @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return service.updateUser(id, request);
    }

    @Operation(summary = "Deactivate a user", description = "Deactivates a user account, preventing them from logging in.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deactivated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PatchMapping("/users/{id}/deactivate")
    public void deactivateUser(
            @Parameter(description = "UUID of the user to deactivate") @PathVariable UUID id) {
        service.deactivateUser(id);
    }

    @Operation(summary = "Bulk upload users", description = "Uploads multiple users from a CSV file into the tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File processed successfully. Returns the results of the bulk upload including number of failures/successes."),
            @ApiResponse(responseCode = "400", description = "Invalid file or file format"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkUploadResponse uploadUsers(
            @Parameter(description = "CSV file containing user data") @RequestParam("file") @NotNull MultipartFile file,
            @Parameter(description = "Whether to auto-assign department-specific tracks to uploaded users") @RequestParam(required = false, defaultValue = "true") Boolean autoAssignTracks
    ) {
        return service.bulkUploadUsers(file, autoAssignTracks);
    }

    @Operation(summary = "Get bulk upload template", description = "Downloads a CSV template format required for the bulk upload API.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV template downloaded successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/users/bulk-upload-template")
    public ResponseEntity<String> getBulkUploadTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tenant-user-bulk-upload-template.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(service.getBulkUploadTemplateCsv());
    }

    @Operation(summary = "Search users", description = "Search for users within the tenant by email, department, role, or active status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved matched users"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/search")
    public Page<UserResponse> searchUsers(
            @Parameter(description = "Filter by user name") @RequestParam(required = false) String name,
            @Parameter(description = "Filter by email address") @RequestParam(required = false) String email,
            @Parameter(description = "Filter by department") @RequestParam(required = false) Department department,
            @Parameter(description = "Filter by user role") @RequestParam(required = false) String role,
            @Parameter(description = "Filter by active status") @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "10") @Positive @Max(100) int size
    ) {
        return service.searchUsers(
                name,
                email,
                department,
                role,
                isActive,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
    }
}
