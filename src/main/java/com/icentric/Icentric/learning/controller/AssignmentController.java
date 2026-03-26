package com.icentric.Icentric.learning.controller;
import com.icentric.Icentric.learning.constants.AssignmentStatus;
import com.icentric.Icentric.learning.dto.BulkAssignmentRequest;
import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.service.AssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AssignmentController {

    private final AssignmentService service;

    public AssignmentController(
            AssignmentService service
    ) {
        this.service = service;
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public UserAssignment assignTrack(
            @Valid @RequestBody CreateAssignmentRequest request
    ) {
        return service.assignTrack(request);
    }
    @PostMapping("/bulk")
    public Map<String, Object> bulkAssign(
            @Valid @RequestBody BulkAssignmentRequest request
    ) {
        return service.bulkAssign(request);
    }
    @GetMapping("/assignments/search")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Page<UserAssignment> searchAssignments(

            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) UUID trackId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "10") @Positive @Max(100) int size

    ) {

        return service.searchAssignments(
                status,
                trackId,
                userId,
                PageRequest.of(page, size, Sort.by("assignedAt").descending())
        );
    }
}
