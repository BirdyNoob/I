package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.common.enums.Department;

import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.ReportRow;
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

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
@Tag(name = "Reports (Admin)", description = "APIs for generating and downloading administrative reports (CSV)")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @Operation(summary = "Get Admin Report", description = "Unified report API. Returns JSON rows by default, or CSV download when format=csv. Supports filtering by department, track, and one or more statuses.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully returned report data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    public ResponseEntity<?> report(
            @Parameter(description = "Filter by department") @RequestParam(required = false) Department department,
            @Parameter(description = "Filter by assignment statuses. Repeat param for multiple statuses.") @RequestParam(required = false) List<AssignmentStatus> status,
            @Parameter(description = "Filter by track ID") @RequestParam(required = false) UUID trackId,
            @Parameter(description = "Response format: json or csv") @RequestParam(defaultValue = "json") String format
    ) {
        if ("csv".equalsIgnoreCase(format)) {
            List<ReportRow> rows = service.getReportData(department, trackId, status);
            String csv = service.toCsv(rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=admin_report.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }
        if (!"json".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("format must be either 'json' or 'csv'");
        }

        List<ReportRow> rows = service.getReportData(department, trackId, status);
        return ResponseEntity.ok(rows);
    }
}
