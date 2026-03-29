package com.icentric.Icentric.content.dto;

import jakarta.validation.constraints.Size;

public record RollbackTrackVersionRequest(
        @Size(max = 1000)
        String changeSummary
) {}
