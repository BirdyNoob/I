package com.icentric.Icentric.identity.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddGroupMemberRequest(
        @NotNull
        UUID userId
) {
}
