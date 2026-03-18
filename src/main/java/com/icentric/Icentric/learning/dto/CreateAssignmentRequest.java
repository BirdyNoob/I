package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateAssignmentRequest(

        UUID userId,
        UUID trackId,
        Instant dueDate

) {}
