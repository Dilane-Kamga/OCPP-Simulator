package com.accenture.nexcharge.simulator.model.dto;

public record StatsDto(
        long totalChargePoints,
        long onlineChargePoints,
        long chargingNow,
        long availableNow,
        long faultedNow,
        long blockedNow,
        long activeSessionsCount,
        double totalPowerKw,
        double todayEnergyKwh,
        long todaySessionsCount,
        long todaySessionsCompleted,
        Long averageSessionDurationMinutes,
        Double averageEnergyPerSessionKwh
) {}
