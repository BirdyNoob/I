package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BulkAssignmentRequest(

        UUID trackId,
        List<UUID> userIds,
        String department,
        Instant dueDate

) {}
