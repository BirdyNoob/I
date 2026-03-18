package com.icentric.Icentric.learning.controller;
import com.icentric.Icentric.learning.dto.CreateAssignmentRequest;
import com.icentric.Icentric.learning.entity.UserAssignment;
import com.icentric.Icentric.learning.service.AssignmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
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
            @RequestBody CreateAssignmentRequest request
    ) {
        return service.assignTrack(request);
    }
}
