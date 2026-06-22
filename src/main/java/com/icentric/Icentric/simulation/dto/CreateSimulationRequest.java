package com.icentric.Icentric.simulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSimulationRequest {
    @NotBlank
    private String title;

    @NotNull
    private Object data; // full simulation JSON payload
}
