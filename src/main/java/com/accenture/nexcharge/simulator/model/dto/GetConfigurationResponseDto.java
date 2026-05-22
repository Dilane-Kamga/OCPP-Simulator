package com.accenture.nexcharge.simulator.model.dto;

import java.util.List;

/**
 * REST response for POST /api/chargepoints/{id}/get-configuration.
 * Mirrors the OCPP 1.6J {@code GetConfigurationConfirmation} shape.
 */
public record GetConfigurationResponseDto(
        List<ConfigurationKeyDto> configurationKey,
        List<String> unknownKey
) {}
