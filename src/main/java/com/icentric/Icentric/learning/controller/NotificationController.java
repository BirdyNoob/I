package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.NotificationResponse;
import com.icentric.Icentric.learning.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
public class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }
    @GetMapping
    public Page<NotificationResponse> getNotifications(

            Authentication auth,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "10") @Positive @Max(100) int size

    ) {

        UUID userId = extractUserId(auth);

        return service.getNotifications(
                userId,
                type,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
    }
    @PatchMapping("/{id}/read")
    public void markAsRead(
            @PathVariable UUID id,
            Authentication auth
    ) {

        UUID userId = extractUserId(auth);

        service.markAsRead(id, userId);
    }
    @PatchMapping("/read-all")
    public void markAll(Authentication auth) {

        UUID userId = extractUserId(auth);

        service.markAllAsRead(userId);
    }
    @GetMapping("/unread-count")
    public long unreadCount(Authentication auth) {

        UUID userId = extractUserId(auth);

        return service.getUnreadCount(userId);
    }

    private UUID extractUserId(Authentication auth) {
        Object userIdRaw = auth != null ? auth.getDetails() : null;
        if (userIdRaw == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing userId in authentication token");
        }
        return UUID.fromString(userIdRaw.toString());
    }
}
