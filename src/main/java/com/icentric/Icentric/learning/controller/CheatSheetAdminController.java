package com.icentric.Icentric.learning.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.icentric.Icentric.learning.service.CheatSheetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/cheat-sheets")
@Tag(name = "Admin Cheat Sheets", description = "Super Admin API for managing cheat sheets globally")
public class CheatSheetAdminController {

    private final CheatSheetService service;

    public CheatSheetAdminController(CheatSheetService service) {
        this.service = service;
    }

    @Operation(summary = "Create or update a cheat sheet", description = "Accepts a flat JSON payload and maps properties dynamically")
    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> createOrUpdate(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(service.createOrUpdateCheatSheet(payload));
    }

    @Operation(summary = "Get all cheat sheets")
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        return ResponseEntity.ok(service.getAllCheatSheets());
    }

    @Operation(summary = "Delete a cheat sheet")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteCheatSheet(id);
        return ResponseEntity.noContent().build();
    }
}
