package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileDto {
    private String name;
    private String department;
    private String avatarInitials;
}
