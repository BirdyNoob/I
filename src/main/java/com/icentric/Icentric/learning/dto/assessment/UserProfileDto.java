package com.icentric.Icentric.learning.dto.assessment;

import com.icentric.Icentric.common.enums.Department;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileDto {
    private String name;
    private Department department;
    private String avatarInitials;
}
