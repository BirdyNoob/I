package com.icentric.Icentric.identity.dto;

import com.icentric.Icentric.common.enums.Department;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full user detail view used by the Super-Admin "view specific user" API.
 */
public record UserDetailResponse(
        UUID id,
        String name,
        String email,
        Department department,
        String role,
        Boolean isActive,
        Instant createdAt,
        String lastActive,
        String location,

        // Overall learning progress across all assigned tracks
        long progressDone,
        long progressTotal,

        // Number of certificates earned
        long certCount,

        List<AssignedTrackDetail> assignedTracks
) {

    public record AssignedTrackDetail(
            UUID trackId,
            String trackName,
            String status,
            Instant completedAt,   // null when not completed
            Instant dueDate,       // null when no due date set
            long progress,         // lessons completed in this track
            long total,            // total lessons in this track
            boolean isOverdue
    ) {}
}
