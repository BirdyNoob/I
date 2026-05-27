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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;
import java.util.UUID;

import com.icentric.Icentric.audit.service.AuditLogsAsyncService;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Validated
@Tag(name = "Audit Logs", description = "APIs for tenant admins to view system audit logs")
public class AuditController {

    private final AuditService auditService;
    private final AuditLogsAsyncService auditLogsAsyncService;

    public AuditController(
            AuditService auditService,
            AuditLogsAsyncService auditLogsAsyncService
    ) {
        this.auditService = auditService;
        this.auditLogsAsyncService = auditLogsAsyncService;
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

    @Operation(summary = "Export audit logs as CSV", description = "Downloads a complete Excel-friendly CSV file containing all audit logs matching the filters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV downloaded successfully")
    })
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo
    ) {
        String csv = auditService.getAuditLogsCsv(action, entityType, userId, createdFrom, createdTo);
        byte[] csvBytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "System_Audit_Logs_" + java.time.LocalDate.now() + ".csv";
        
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }

    @Operation(summary = "Export audit logs as PDF", description = "Generates and downloads a high-fidelity landscape A4 PDF report containing all matching audit logs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated and downloaded successfully")
    })
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo
    ) {
        byte[] pdf = auditService.getAuditLogsReportPdf(action, entityType, userId, createdFrom, createdTo);
        String filename = "System_Audit_Logs_Report_" + java.time.LocalDate.now() + ".pdf";
        
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(
            summary = "Trigger asynchronous audit logs email",
            description = "Triggers background landscape PDF compilation and emails the report directly to the logged-in administrator."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Logs report successfully queued")
    })
    @PostMapping("/email")
    public ResponseEntity<com.icentric.Icentric.audit.dto.AuditLogsEmailResponse> queueAuditLogsEmail(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo
    ) {
        String recipientEmail = auditService.currentActorUserEmail();
        String tenantSlug = com.icentric.Icentric.tenant.TenantContext.getTenant();
        UUID actorUserId = com.icentric.Icentric.common.security.SecurityUtils.currentUserIdOrNull();

        // 1. Enforce active compilation debouncing
        if (auditLogsAsyncService.isCompiling(recipientEmail, tenantSlug)) {
            String busyMessage = "A System Audit Logs report compilation is already in progress for your account. Please check your inbox shortly.";
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                    .body(new com.icentric.Icentric.audit.dto.AuditLogsEmailResponse(false, busyMessage, recipientEmail));
        }

        // 2. Enforce 6-hour request rate-limiting
        long remainingSeconds = auditLogsAsyncService.getRateLimitRemainingSeconds(recipientEmail, tenantSlug);
        if (remainingSeconds > 0) {
            long hours = remainingSeconds / 3600;
            long minutes = (remainingSeconds % 3600) / 60;
            String limitMessage = String.format(
                "To prevent system spam, you can only request this report once every 6 hours. " +
                "You can request another report in %d hours and %d minutes.", 
                hours, minutes
            );

            // Log Rate-Limited Audit Event
            if (actorUserId != null) {
                auditService.log(
                    actorUserId,
                    AuditAction.AUDIT_LOGS_EMAIL_RATE_LIMITED,
                    "USER",
                    actorUserId.toString(),
                    "Logs report request rate-limited. Time remaining: " + hours + "h " + minutes + "m."
                );
            }

            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                    .body(new com.icentric.Icentric.audit.dto.AuditLogsEmailResponse(false, limitMessage, recipientEmail));
        }

        // 3. Log Queued Audit Event
        if (actorUserId != null) {
            auditService.log(
                actorUserId,
                AuditAction.AUDIT_LOGS_EMAIL_QUEUED,
                "USER",
                actorUserId.toString(),
                "System Audit Logs report compilation triggered and queued."
            );
        }

        auditLogsAsyncService.compileAndEmailLogs(
            recipientEmail, action, entityType, userId, createdFrom, createdTo, tenantSlug
        );

        String message = "Your System Audit Logs & Security Activity Report generation has been queued. You will receive the compiled PDF at " + recipientEmail + " shortly.";
        return ResponseEntity.accepted().body(new com.icentric.Icentric.audit.dto.AuditLogsEmailResponse(true, message, recipientEmail));
    }
}
