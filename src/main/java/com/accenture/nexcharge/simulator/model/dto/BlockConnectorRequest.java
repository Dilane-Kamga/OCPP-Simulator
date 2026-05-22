package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotBlank;

public record BlockConnectorRequest(
        @NotBlank(message = "reason must not be blank") String reason
) {}
