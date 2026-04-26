package com.icentric.Icentric.learning.dto.assessment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopicBreakdownDto {
    private String topic;
    private Integer score;
}
