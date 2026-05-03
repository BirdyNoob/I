package com.icentric.Icentric.identity.controller;

import com.icentric.Icentric.identity.dto.ChangePasswordRequest;
import com.icentric.Icentric.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@Validated
@Tag(name = "User Profile", description = "APIs for logged-in users (learners, admins, etc.) to manage their profile")
public class UserProfileController {

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Change Password", description = "Allows the currently authenticated user to change their password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or incorrect old password"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            org.springframework.security.core.Authentication authentication
    ) {
        if (authentication == null || authentication.getDetails() == null) {
            return ResponseEntity.status(401).build();
        }

        UUID userId;
        try {
            userId = UUID.fromString(authentication.getDetails().toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).build();
        }
        
        try {
            userService.changePassword(userId, request.oldPassword(), request.newPassword());
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
