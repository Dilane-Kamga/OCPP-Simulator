package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;

public record ConnectorDto(
        Integer connectorId,
        ConnectorStatus status,
        Double currentPowerKw,
        Double currentAmps,
        Double voltage,
        Double temperatureCelsius,
        Double totalEnergyKwh,
        String errorCode
) {}
