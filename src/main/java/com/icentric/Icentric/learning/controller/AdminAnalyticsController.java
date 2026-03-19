package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.AdminAnalyticsResponse;
import com.icentric.Icentric.learning.dto.DepartmentPerformanceResponse;
import com.icentric.Icentric.learning.dto.RiskUserResponse;
import com.icentric.Icentric.learning.dto.WeakLessonResponse;
import com.icentric.Icentric.learning.service.AdminAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    @GetMapping("/risk-users")
    public List<RiskUserResponse> riskUsers() {
        return service.getRiskUsers();
    }
    @GetMapping("/weak-lessons")
    public List<WeakLessonResponse> weakLessons() {
        return service.getWeakLessons();
    }
    @GetMapping("/department-performance")
    public List<DepartmentPerformanceResponse> departmentPerformance() {
        return service.getDepartmentPerformance();
    }
}
