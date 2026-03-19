package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.AdminAnalyticsResponse;
import com.icentric.Icentric.learning.service.AdminAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {

    private final AdminAnalyticsService service;

    public AdminAnalyticsController(AdminAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public AdminAnalyticsResponse overview() {
        return service.getOverview();
    }
}
