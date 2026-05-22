package com.accenture.nexcharge.simulator.model.dto;

import jakarta.validation.constraints.NotNull;

public record RemoteStopRequest(@NotNull Integer transactionId) {}
