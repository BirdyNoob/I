package com.icentric.Icentric.audit.controller;

import com.icentric.Icentric.audit.constants.AuditAction;
import com.icentric.Icentric.audit.dto.AuditLogResponse;
import com.icentric.Icentric.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Validated
@Tag(name = "Audit Logs", description = "APIs for tenant admins to view system audit logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Operation(summary = "Get audit logs", description = "Retrieves a paginated list of audit logs for the current tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved audit logs"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin role required")
    })
    @GetMapping
    public Page<AuditLogResponse> getLogs(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @PositiveOrZero Integer page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "10") @Positive @Max(100) Integer size,
            @Parameter(description = "Filter by audit action") @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Filter by entity type") @RequestParam(required = false) String entityType,
            @Parameter(description = "Filter by actor user ID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Filter logs created at or after this instant") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @Parameter(description = "Filter logs created at or before this instant") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo
    ) {
        return auditService.getLogs(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
                action,
                entityType,
                userId,
                createdFrom,
                createdTo
        );
    }
}
