package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.*;
import com.icentric.Icentric.learning.service.AdminAnalyticsService;
import com.icentric.Icentric.learning.service.AdminAnalyticsService.OverdueNotificationResult;
import com.icentric.Icentric.learning.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@Tag(name = "Analytics (Admin)", description = "APIs for administrative analytics and dashboard metrics")
public class AdminAnalyticsController {

    private final AdminAnalyticsService service;
    private final AssignmentService assignmentService;

    public AdminAnalyticsController(AdminAnalyticsService service, AssignmentService assignmentService) {
        this.service = service;
        this.assignmentService = assignmentService;
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

    @Operation(summary = "Get department leaderboard", description = "Retrieves a ranked compliance and quiz performance leaderboard of departments.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved department leaderboard")
    })
    @GetMapping("/department-leaderboard")
    public DepartmentLeaderboardResponse departmentLeaderboard() {
        return service.getDepartmentLeaderboard();
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
            @RequestBody(required = false) NotifyOverdueRequest request
    ) {
        java.util.List<UUID> userIds = (request != null) ? request.userIds() : null;
        return ResponseEntity.ok(service.notifyOverdueUsers(userIds));
    }

    @Operation(summary = "Get lagging learners", description = "Retrieves a list of learners who have exhausted their assessment attempts without passing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved lagging learners list")
    })
    @GetMapping("/lagging-learners")
    public List<LaggingLearnerResponse> laggingLearners() {
        return service.getLaggingLearners();
    }

    @Operation(summary = "Reset assessment attempts", description = "Clears all assessment attempt history for a specific user and assessment configuration.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully reset assessment attempts"),
            @ApiResponse(responseCode = "403", description = "Forbidden if standard manager has no scoping permissions")
    })
    @PostMapping("/attempts/reset")
    public ResponseEntity<Void> resetAttempts(@Valid @RequestBody ResetAttemptsRequest request) {
        service.resetAttempts(request.userId(), request.assessmentConfigId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Assign remedial track", description = "Assigns a remedial learning track to a lagging learner with optional due date.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully assigned remedial track"),
            @ApiResponse(responseCode = "403", description = "Forbidden if standard manager has no scoping permissions")
    })
    @PostMapping("/attempts/remediate")
    public ResponseEntity<Void> remediate(@Valid @RequestBody RemediationRequest request) {
        assignmentService.assignTrack(new CreateAssignmentRequest(
                request.userId(),
                request.trackId(),
                request.dueDate()
        ));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get assessment reset history", description = "Retrieves audit logs of learners who failed assessments and had their attempts reset by a manager.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved reset history")
    })
    @GetMapping("/attempts/reset-history")
    public List<AssessmentResetLogResponse> getResetHistory() {
        return service.getAssessmentResetHistory();
    }
}

