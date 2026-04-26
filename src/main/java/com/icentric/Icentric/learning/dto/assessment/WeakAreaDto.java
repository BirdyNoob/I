package com.icentric.Icentric.learning.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WeakAreaDto {
    private String topic;
    private Integer score;
    private String moduleReviewUrl;
}
