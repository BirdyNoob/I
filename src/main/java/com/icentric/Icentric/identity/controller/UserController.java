package com.icentric.Icentric.identity.controller;

import com.icentric.Icentric.identity.dto.BulkUploadResponse;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
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
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public Page<UserResponse> getUsers(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "10") @Positive @Max(100) int size
    ) {
        return service.getUsers(department, role, isActive, PageRequest.of(page, size));
    }

    @PostMapping("/users/create")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return service.createUser(request);
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return service.updateUser(id, request);
    }

    @PatchMapping("/users/{id}/deactivate")
    public void deactivateUser(@PathVariable UUID id) {
        service.deactivateUser(id);
    }
    @PostMapping("/bulk-upload")
    public BulkUploadResponse uploadUsers(
            @RequestParam("file") @NotNull MultipartFile file
    ) {
        return service.bulkUploadUsers(file);
    }

    @GetMapping("/users/bulk-upload-template")
    public ResponseEntity<String> getBulkUploadTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tenant-user-bulk-upload-template.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(service.getBulkUploadTemplateCsv());
    }
    @GetMapping("/search")
    public Page<UserResponse> searchUsers(

            @RequestParam(required = false) String email,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "10") @Positive @Max(100) int size

    ) {

        return service.searchUsers(
                email,
                department,
                role,
                isActive,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
    }
}
