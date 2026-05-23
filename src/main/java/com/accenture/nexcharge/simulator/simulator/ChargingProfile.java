package com.accenture.nexcharge.simulator.simulator;

import java.time.Duration;
import java.util.Random;

public class ChargingProfile {

    private static final double NOMINAL_VOLTAGE = 230.0;
    private static final double VOLTAGE_NOISE_RANGE = 5.0;
    private static final double POWER_NOISE_RATIO = 0.03;
    private static final double TEMPERATURE_BASE_CELSIUS = 20.0;
    private static final double TEMPERATURE_DELTA_CELSIUS = 25.0;

    private static final double SIMULATED_FULL_CHARGE_MINUTES = 240.0;

    private final double maxPowerKw;
    private final double accelerationFactor;
    private final Random random;

    private double socPercent = 0.0;
    private double totalEnergyDeliveredKwh = 0.0;

    public ChargingProfile(double maxPowerKw, double accelerationFactor, Random random) {
        this.maxPowerKw = maxPowerKw;
        this.accelerationFactor = accelerationFactor;
        this.random = random;
    }

    public PowerSnapshot tick(Duration realElapsed) {
        return tick(realElapsed, maxPowerKw);
    }

    /**
     * Tick with a power cap (used for load balancing across connectors sharing a borne).
     * The connector's natural power curve is clamped to {@code powerCapKw}.
     */
    public PowerSnapshot tick(Duration realElapsed, double powerCapKw) {
        double simulatedSeconds = realElapsed.toMillis() / 1000.0 * accelerationFactor;
        double simulatedMinutes = simulatedSeconds / 60.0;

        double socIncrement = (simulatedMinutes / SIMULATED_FULL_CHARGE_MINUTES) * 100.0;
        socPercent = Math.min(100.0, socPercent + socIncrement);

        double basePowerKw = Math.min(computePowerForSoc(socPercent), powerCapKw);
        double noisyPowerKw = applyGaussianNoise(basePowerKw);
        noisyPowerKw = Math.max(0.0, noisyPowerKw);

        double voltage = NOMINAL_VOLTAGE + random.nextDouble() * VOLTAGE_NOISE_RANGE;
        double currentAmps = (noisyPowerKw * 1000.0) / voltage;
        double temperatureCelsius = TEMPERATURE_BASE_CELSIUS
                + (noisyPowerKw / maxPowerKw) * TEMPERATURE_DELTA_CELSIUS;

        double realHours = realElapsed.toMillis() / 1000.0 / 3600.0;
        totalEnergyDeliveredKwh += noisyPowerKw * realHours * accelerationFactor;

        return new PowerSnapshot(
                noisyPowerKw, voltage, currentAmps, temperatureCelsius,
                totalEnergyDeliveredKwh, socPercent
        );
    }

    private double computePowerForSoc(double soc) {
        if (soc <= 5.0) {
            return maxPowerKw * (soc / 5.0);
        }
        if (soc <= 80.0) {
            return maxPowerKw;
        }
        if (soc <= 95.0) {
            double slope = (maxPowerKw * 0.3 - maxPowerKw) / (95.0 - 80.0);
            return maxPowerKw + slope * (soc - 80.0);
        }
        double slope = (maxPowerKw * 0.1 - maxPowerKw * 0.3) / (100.0 - 95.0);
        return maxPowerKw * 0.3 + slope * (soc - 95.0);
    }

    private double applyGaussianNoise(double value) {
        double noise = random.nextGaussian() * POWER_NOISE_RATIO;
        return value * (1.0 + noise);
    }

    public double getSocPercent() {
        return socPercent;
    }

    public double getTotalEnergyDeliveredKwh() {
        return totalEnergyDeliveredKwh;
    }

    public boolean isComplete() {
        return socPercent >= 100.0;
    }
}
