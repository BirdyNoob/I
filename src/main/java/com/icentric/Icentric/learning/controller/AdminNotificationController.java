package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.AdminNotificationResponse;
import com.icentric.Icentric.learning.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@Validated
public class AdminNotificationController {

    private final NotificationService service;

    public AdminNotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AdminNotificationResponse> getNotifications(
            @RequestParam(defaultValue = "0") @PositiveOrZero Integer page,
            @RequestParam(defaultValue = "10") @Positive @Max(100) Integer size
    ) {
        return service.getAdminNotifications(
                PageRequest.of(page, size)
        );
    }
}
