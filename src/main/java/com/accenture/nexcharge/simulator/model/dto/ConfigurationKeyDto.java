package com.accenture.nexcharge.simulator.model.dto;

/**
 * A single configuration key/value pair, matching the OCPP 1.6J {@code KeyValueType}.
 */
public record ConfigurationKeyDto(String key, boolean readonly, String value) {}
