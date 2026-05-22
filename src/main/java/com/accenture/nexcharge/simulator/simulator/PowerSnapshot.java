package com.accenture.nexcharge.simulator.simulator;

public record PowerSnapshot(
        double powerKw,
        double voltage,
        double currentAmps,
        double temperatureCelsius,
        double totalEnergyKwh,
        double socPercent
) {}
