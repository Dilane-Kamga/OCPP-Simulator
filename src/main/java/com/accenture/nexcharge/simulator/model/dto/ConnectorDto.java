package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;

import java.time.Instant;

public record ConnectorDto(
        Integer connectorId,
        ConnectorStatus status,
        Double currentPowerKw,
        Double currentAmps,
        Double voltage,
        Double temperatureCelsius,
        Double totalEnergyKwh,
        String errorCode,
        boolean blocked,
        String blockedReason,
        Instant blockedAt
) {}
