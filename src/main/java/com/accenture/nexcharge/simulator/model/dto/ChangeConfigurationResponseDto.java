package com.accenture.nexcharge.simulator.model.dto;

/**
 * REST response for POST /api/chargepoints/{id}/change-configuration.
 * The {@code status} maps 1-to-1 with {@link eu.chargetime.ocpp.model.core.ConfigurationStatus}
 * (Accepted, Rejected, RebootRequired, NotSupported).
 */
public record ChangeConfigurationResponseDto(String status) {}
