package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SceneAnswerRequest {
    private String answer; // A, B, C, or D
}
