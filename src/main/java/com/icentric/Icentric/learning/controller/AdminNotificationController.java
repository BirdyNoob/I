package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.AdminNotificationResponse;
import com.icentric.Icentric.learning.dto.UnreadNotificationCountResponse;
import com.icentric.Icentric.learning.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Notifications (Admin)", description = "APIs for admins to view system notifications")
public class AdminNotificationController {

    private final NotificationService service;

    public AdminNotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "Get admin notifications", description = "Retrieves a paginated list of all system notifications relevant to administrators.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved notifications"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    public Page<AdminNotificationResponse> getNotifications(
            Authentication auth,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @PositiveOrZero Integer page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "10") @Positive @Max(100) Integer size
    ) {
        return service.getAdminNotifications(
                extractUserId(auth),
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
    }

    @Operation(summary = "Mark single admin notification as read", description = "Marks one admin notification as read for the authenticated admin user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    @PatchMapping("/{id}/read")
    public void markAsRead(
            @Parameter(description = "UUID of the notification to mark read") @PathVariable UUID id,
            Authentication auth
    ) {
        service.markAsRead(id, extractUserId(auth));
    }

    @Operation(summary = "Mark all admin notifications as read", description = "Marks all unread notifications as read for the authenticated admin user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All notifications marked as read"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PatchMapping("/read-all")
    public void markAllAsRead(Authentication auth) {
        service.markAllAsRead(extractUserId(auth));
    }

    @Operation(summary = "Get unread admin notification count", description = "Returns unread notification count for the authenticated admin user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread count returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount(Authentication auth) {
        return new UnreadNotificationCountResponse(service.getUnreadCount(extractUserId(auth)));
    }

    private UUID extractUserId(Authentication auth) {
        Object userIdRaw = auth != null ? auth.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        return UUID.fromString(userIdRaw.toString());
    }
}
