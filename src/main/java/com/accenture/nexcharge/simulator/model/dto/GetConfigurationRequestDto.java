package com.accenture.nexcharge.simulator.model.dto;

import java.util.List;

/**
 * REST request body for POST /api/chargepoints/{id}/get-configuration.
 * {@code keys} is optional: null or empty → fetch all known keys.
 */
public record GetConfigurationRequestDto(List<String> keys) {}
