package com.icentric.Icentric.learning.dto;

import java.util.List;
import java.util.UUID;

public record NotifyOverdueRequest(
        List<UUID> userIds
) {}
