package com.icentric.Icentric.learning.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReminderConfigResponse(
        UUID tenantId,
        boolean reminderEnabled,
        List<Integer> reminderOffsetsHours,
        boolean escalationEnabled,
        int escalationDelayHours,
        Instant updatedAt
) {}
