package com.icentric.Icentric.content.dto;

import jakarta.validation.constraints.Size;

public record CreateTrackVersionRequest(
        @Size(max = 1000)
        String changeSummary
) {}
