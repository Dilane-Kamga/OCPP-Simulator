package com.accenture.nexcharge.simulator.model.dto;

import java.time.Instant;

public record AuthorizedTagDto(
        String idTag,
        String parentIdTag,
        Instant expiryDate,
        Instant createdAt,
        Boolean blocked
) {}
