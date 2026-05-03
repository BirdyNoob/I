package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.service.CheatSheetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cheat-sheets")
@Tag(name = "Cheat Sheets", description = "Public API for fetching cheat sheets by department")
public class CheatSheetController {

    private final CheatSheetService service;

    public CheatSheetController(CheatSheetService service) {
        this.service = service;
    }

    @Operation(summary = "Get cheat sheets", description = "Fetches cheat sheets applicable to the logged-in user's department, plus global ones")
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCheatSheets() {
        return ResponseEntity.ok(service.getCheatSheetsForCurrentUser());
    }
}
