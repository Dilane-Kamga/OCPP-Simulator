package com.accenture.nexcharge.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("simulator")
public record SimulatorProperties(
        boolean enabled,
        int accelerationFactor,
        int heartbeatIntervalSeconds,
        int meterIntervalSeconds,
        double autoSessionProbability,
        double randomEventProbability,
        List<ChargePointConfig> chargePoints,
        List<String> rfidTags
) {
    public record ChargePointConfig(
            String id,
            String site,
            String vendor,
            String model,
            String serial,
            double maxPowerKw,
            int connectors,
            String firmware
    ) {}
}
