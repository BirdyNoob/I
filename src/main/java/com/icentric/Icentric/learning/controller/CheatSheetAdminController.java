package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.service.CheatSheetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/cheat-sheets")
@Tag(name = "Admin Cheat Sheets", description = "Platform Admin API for managing cheat sheets")
public class CheatSheetAdminController {

    private final CheatSheetService service;

    public CheatSheetAdminController(CheatSheetService service) {
        this.service = service;
    }

    /**
     * Create or update a cheat sheet.
     *
     * Required fields: title, type
     * Optional: trackId (UUID) — if omitted, the sheet is GLOBAL (all learners see it)
     *           description, plus any extra fields stored in the JSONB data column
     *
     * Example body:
     * {
     *   "title": "Phishing Red Flags",
     *   "type": "PHISHING",
     *   "trackId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
     *   "description": "Quick reference for spotting phishing emails",
     *   "tips": ["Check sender domain", "Hover before clicking"]
     * }
     */
    @Operation(summary = "Create or update a cheat sheet",
               description = "Pass trackId to scope to a track, or omit for a global sheet visible to all learners")
    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> createOrUpdate(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(service.createOrUpdateCheatSheet(payload));
    }

    /**
     * Create or update multiple cheat sheets in bulk.
     */
    @Operation(summary = "Create or update multiple cheat sheets in bulk",
               description = "Accepts a list of cheat sheet payloads and processes them in a single transaction.")
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> createOrUpdateBulk(@RequestBody List<Map<String, Object>> payloads) {
        return ResponseEntity.ok(service.createOrUpdateCheatSheets(payloads));
    }

    /**
     * Get all cheat sheets (admin view — no filtering).
     */
    @Operation(summary = "Get all cheat sheets (admin)")
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        return ResponseEntity.ok(service.getAllCheatSheets());
    }

    /**
     * Create or update a cheat sheet for a specific module (moduleId from path).
     * No need to include moduleId in the body.
     */
    @Operation(summary = "Create or update a cheat sheet for a module",
               description = "moduleId is taken from the path — no need to include it in the body")
    @PostMapping("/module/{moduleId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> createForModule(
            @PathVariable UUID moduleId,
            @RequestBody Map<String, Object> payload) {
        payload.put("moduleId", moduleId.toString());
        return ResponseEntity.ok(service.createOrUpdateCheatSheet(payload));
    }

    /**
     * Bulk create cheat sheets for a specific module (moduleId from path).
     * No need to include moduleId in each payload.
     */
    @Operation(summary = "Bulk create cheat sheets for a module",
               description = "moduleId is applied to all sheets from the path")
    @PostMapping("/module/{moduleId}/bulk")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> createBulkForModule(
            @PathVariable UUID moduleId,
            @RequestBody List<Map<String, Object>> payloads) {
        payloads.forEach(p -> p.put("moduleId", moduleId.toString()));
        return ResponseEntity.ok(service.createOrUpdateCheatSheets(payloads));
    }

    /**
     * Get cheat sheets for a specific module.
     */
    @Operation(summary = "Get cheat sheets for a specific module")
    @GetMapping("/module/{moduleId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getByModule(@PathVariable UUID moduleId) {
        return ResponseEntity.ok(service.getCheatSheetsByModule(moduleId));
    }

    /**
     * Delete a cheat sheet by ID.
     */
    @Operation(summary = "Delete a cheat sheet")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteCheatSheet(id);
        return ResponseEntity.noContent().build();
    }
}
