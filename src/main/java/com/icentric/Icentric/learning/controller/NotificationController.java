package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.constants.NotificationType;
import com.icentric.Icentric.learning.dto.NotificationResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Validated
@PreAuthorize("hasRole('LEARNER')")
@Tag(name = "Notifications (Learner)", description = "APIs for learners to view and manage their notifications")
public class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "Get user notifications", description = "Retrieves a paginated list of notifications for the authenticated learner, optionally filtered by type.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved notifications"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    public Page<NotificationResponse> getNotifications(
            Authentication auth,
            @Parameter(description = "Filter by notification type") @RequestParam(required = false) NotificationType type,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "10") @Positive @Max(100) int size
    ) {

        UUID userId = extractUserId(auth);

        return service.getNotifications(
                userId,
                type,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
    }

    @Operation(summary = "Mark single notification as read", description = "Marks a specific notification as read by its UUID.")
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

        UUID userId = extractUserId(auth);

        service.markAsRead(id, userId);
    }

    @Operation(summary = "Mark all notifications as read", description = "Marks all unread notifications of the authenticated user as read.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All notifications marked as read"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PatchMapping("/read-all")
    public void markAll(Authentication auth) {

        UUID userId = extractUserId(auth);

        service.markAllAsRead(userId);
    }

    @Operation(summary = "Get unread notification count", description = "Returns the total number of unread notifications for the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved unread count"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount(Authentication auth) {

        UUID userId = extractUserId(auth);

        return new UnreadNotificationCountResponse(
                service.getUnreadCount(userId)
        );
    }

    private UUID extractUserId(Authentication auth) {
        Object userIdRaw = auth != null ? auth.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        return UUID.fromString(userIdRaw.toString());
    }
}
