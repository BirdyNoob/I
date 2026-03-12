package com.icentric.Icentric.content.dto;

import java.util.List;
import java.util.UUID;

public record TrackDetailResponse(
        UUID id,
        String title,
        List<ModuleResponse> modules
) {}
