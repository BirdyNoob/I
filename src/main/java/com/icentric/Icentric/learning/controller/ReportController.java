package com.icentric.Icentric.learning.controller;

import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.service.ReportService;
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
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/completion")
    public ResponseEntity<StreamingResponseBody> completionReport(

            @RequestParam(required = false) String department,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) UUID trackId

    ) {

        StreamingResponseBody stream =
                service.streamCompletionReport(department, status, trackId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=completion_report.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(stream);
    }

    @GetMapping("/risk")
    public ResponseEntity<StreamingResponseBody> riskReport(

            @RequestParam(required = false) String department,
            @RequestParam(required = false) UUID trackId

    ) {

        var stream = service.streamRiskReport(department, trackId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=risk_report.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(stream);
    }
}
