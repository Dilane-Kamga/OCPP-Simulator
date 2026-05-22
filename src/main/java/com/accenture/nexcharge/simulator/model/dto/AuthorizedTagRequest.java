package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record AuthorizedTagRequest(
        @NotBlank String idTag,
        String parentIdTag,
        Instant expiryDate,
        Boolean blocked
) {}
