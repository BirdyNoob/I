package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.dto.ReminderConfigRequest;
import com.icentric.Icentric.learning.dto.ReminderConfigResponse;
import com.icentric.Icentric.learning.service.ReminderConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reminder-config")
@Tag(name = "Reminder Config", description = "Tenant-admin APIs for assignment reminder and escalation settings.")
public class ReminderConfigController {

    private final ReminderConfigService reminderConfigService;

    public ReminderConfigController(ReminderConfigService reminderConfigService) {
        this.reminderConfigService = reminderConfigService;
    }

    @Operation(summary = "Get reminder config", description = "Returns the current tenant reminder and escalation settings.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Config returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ReminderConfigResponse getConfig() {
        return reminderConfigService.getCurrentConfig();
    }

    @Operation(summary = "Save reminder config", description = "Creates or updates the current tenant reminder and escalation settings.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Config saved"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ReminderConfigResponse saveConfig(@Valid @RequestBody ReminderConfigRequest request) {
        return reminderConfigService.saveCurrentConfig(request);
    }
}
