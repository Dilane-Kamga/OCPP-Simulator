package com.accenture.nexcharge.simulator.model.dto;

import com.accenture.nexcharge.simulator.model.enums.LiveEventType;

import java.time.Instant;
import java.util.Map;

public record LiveEventDto(
        LiveEventType type,
        String chargePointId,
        Integer connectorId,
        Map<String, Object> data,
        Instant timestamp
) {
    public static LiveEventDto of(LiveEventType type, String chargePointId, Map<String, Object> data) {
        return new LiveEventDto(type, chargePointId, null, data, Instant.now());
    }

    public static LiveEventDto of(LiveEventType type, String chargePointId, Integer connectorId, Map<String, Object> data) {
        return new LiveEventDto(type, chargePointId, connectorId, data, Instant.now());
    }
}
