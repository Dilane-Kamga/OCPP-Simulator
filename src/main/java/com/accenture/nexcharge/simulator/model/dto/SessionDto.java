package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.SessionStatus;

import java.time.Instant;

public record SessionDto(
        Long id,
        Integer transactionId,
        String chargePointId,
        Integer connectorId,
        String idTag,
        Instant startTime,
        Instant stopTime,
        Double meterStartWh,
        Double meterStopWh,
        Double energyDeliveredKwh,
        String stopReason,
        SessionStatus status,
        Long durationMinutes
) {}
