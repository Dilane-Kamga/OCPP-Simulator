package com.accenture.nexcharge.simulator.model.dto;

import java.time.Instant;

public record MeterValueDto(
        Instant timestamp,
        Integer connectorId,
        Integer transactionId,
        String measurand,
        Double value,
        String unit
) {}
