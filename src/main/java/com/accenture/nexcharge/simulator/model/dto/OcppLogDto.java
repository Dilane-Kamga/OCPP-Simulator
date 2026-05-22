package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.LogDirection;

import java.time.Instant;

public record OcppLogDto(
        Long id,
        String chargePointId,
        LogDirection direction,
        String action,
        String payload,
        Instant timestamp
) {}
