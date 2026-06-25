package com.icentric.Icentric.simulation.dto;

import lombok.Data;
import java.util.Map;

@Data
public class SimulationSubmitRequest {
    private Map<String, String> answers;
}
