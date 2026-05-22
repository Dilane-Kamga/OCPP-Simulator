package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RemoteStartRequest(
        @NotBlank String idTag,
        @NotNull Integer connectorId
) {}
