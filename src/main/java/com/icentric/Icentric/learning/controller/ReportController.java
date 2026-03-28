package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "Reports (Admin)", description = "APIs for generating and downloading administrative reports (CSV)")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @Operation(summary = "Download Completion Report", description = "Generates and streams a CSV report showing track completion status for users. Can be filtered by department, status, and track.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully generated completion report CSV"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/completion")
    public ResponseEntity<StreamingResponseBody> completionReport(
            @Parameter(description = "Filter by department") @RequestParam(required = false) String department,
            @Parameter(description = "Filter by assignment status") @RequestParam(required = false) AssignmentStatus status,
            @Parameter(description = "Filter by track ID") @RequestParam(required = false) UUID trackId
    ) {

        StreamingResponseBody stream =
                service.streamCompletionReport(department, status, trackId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=completion_report.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(stream);
    }

    @Operation(summary = "Download Risk Report", description = "Generates and streams a CSV report identifying users at risk (e.g. falling behind). Can be filtered by department and track.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully generated risk report CSV"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/risk")
    public ResponseEntity<StreamingResponseBody> riskReport(
            @Parameter(description = "Filter by department") @RequestParam(required = false) String department,
            @Parameter(description = "Filter by track ID") @RequestParam(required = false) UUID trackId
    ) {

        var stream = service.streamRiskReport(department, trackId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=risk_report.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(stream);
    }
}
