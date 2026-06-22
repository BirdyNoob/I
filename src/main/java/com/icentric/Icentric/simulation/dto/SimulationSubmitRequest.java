package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class SimulationSubmitRequest {
    private Map<String, String> answers;
}
