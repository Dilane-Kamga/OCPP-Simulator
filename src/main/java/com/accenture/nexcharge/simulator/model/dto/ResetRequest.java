package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.Pattern;

public record ResetRequest(
        @Pattern(regexp = "Soft|Hard", message = "type must be Soft or Hard")
        String type
) {}
