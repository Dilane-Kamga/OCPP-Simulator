package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeConfigurationRequestDto(
        @NotBlank String key,
        @NotBlank String value
) {}
