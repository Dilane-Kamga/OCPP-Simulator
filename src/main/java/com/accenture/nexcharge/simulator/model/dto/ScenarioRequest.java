package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ScenarioRequest(
        @NotBlank String scenario,
        String chargePointId,
        Integer connectorId
) {
    public ScenarioRequest(String scenario, String chargePointId) {
        this(scenario, chargePointId, null);
    }
}
