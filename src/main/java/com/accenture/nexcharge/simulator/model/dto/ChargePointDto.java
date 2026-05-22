package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;

import java.time.Instant;
import java.util.List;

public record ChargePointDto(
        String chargePointId,
        String vendor,
        String model,
        String serialNumber,
        String firmwareVersion,
        ChargePointStatus status,
        boolean online,
        Instant lastHeartbeat,
        Instant registeredAt,
        String errorCode,
        List<ConnectorDto> connectors
) {}
