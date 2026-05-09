package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.*;
import com.icentric.Icentric.learning.service.AdminAnalyticsService;
import com.icentric.Icentric.learning.service.AdminAnalyticsService.OverdueNotificationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@Tag(name = "Analytics (Admin)", description = "APIs for administrative analytics and dashboard metrics")
public class AdminAnalyticsController {

    private final AdminAnalyticsService service;

    public AdminAnalyticsController(AdminAnalyticsService service) {
        this.service = service;
    }

    @Operation(summary = "Get overall overview", description = "Retrieves high-level analytics overview metrics for the admin dashboard.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved overview")
    })
    @GetMapping("/overview")
    public AdminAnalyticsResponse overview() {
        return service.getOverview();
    }

    @Operation(summary = "Get at-risk users", description = "Retrieves a list of users identified as 'at risk' based on their learning progress.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved at-risk users")
    })
    @GetMapping("/risk-users")
    public List<RiskUserResponse> riskUsers() {
        return service.getRiskUsers();
    }

    @Operation(summary = "Get weak lessons", description = "Retrieves analytics on lessons where users are struggling or scoring poorly.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved weak lessons analytics")
    })
    @GetMapping("/weak-lessons")
    public List<WeakLessonResponse> weakLessons() {
        return service.getWeakLessons();
    }

    @Operation(summary = "Get department performance", description = "Retrieves performance metrics aggregated by department.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved department performance analytics")
    })
    @GetMapping("/department-performance")
    public List<DepartmentPerformanceResponse> departmentPerformance() {
        return service.getDepartmentPerformance();
    }

    @Operation(summary = "Get admin dashboard data", description = "Retrieves aggregated data to populate the main admin analytics dashboard.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved dashboard data")
    })
    @GetMapping("/dashboard")
    public AdminOverviewResponse dashboard() {
        return service.getDashboard();
    }
    @Operation(
            summary = "Notify overdue learners",
            description = "Sends an email reminder to all learners (or specific users) who have overdue assignments. "
                    + "Pass a JSON body with a list of userIds to target specific users, or send an empty body to notify all overdue learners in the tenant."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification result returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping("/notify-overdue")
    public ResponseEntity<OverdueNotificationResult> notifyOverdue(
            @RequestBody(required = false) List<UUID> userIds
    ) {
        return ResponseEntity.ok(service.notifyOverdueUsers(userIds));
    }
}
