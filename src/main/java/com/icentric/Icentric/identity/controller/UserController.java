package com.icentric.Icentric.identity.controller;

import com.icentric.Icentric.identity.dto.BulkUploadResponse;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.dto.UpdateUserRequest;
import com.icentric.Icentric.identity.dto.UserResponse;
import com.icentric.Icentric.identity.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.getUsers(department, role, isActive, PageRequest.of(page, size));
    }

    @PostMapping("/users/create")
    public UserResponse createUser(@RequestBody CreateUserRequest request) {
        return service.createUser(request);
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateUserRequest request
    ) {
        return service.updateUser(id, request);
    }

    @PatchMapping("/users/{id}/deactivate")
    public void deactivateUser(@PathVariable UUID id) {
        service.deactivateUser(id);
    }
    @PostMapping("/bulk-upload")
    public BulkUploadResponse uploadUsers(
            @RequestParam("file") MultipartFile file
    ) {
        return service.bulkUploadUsers(file);
    }
}
