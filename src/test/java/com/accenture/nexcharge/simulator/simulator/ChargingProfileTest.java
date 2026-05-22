package com.accenture.nexcharge.simulator.simulator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ChargingProfileTest {

    private static final long DETERMINISTIC_SEED = 42L;

    @Test
    void initialPowerIsZero() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        assertThat(profile.getSocPercent()).isZero();
        assertThat(profile.getTotalEnergyDeliveredKwh()).isZero();
    }

    @Test
    void rampUpPhaseStartsBelowMaxPower() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(10));
        assertThat(snap.powerKw()).isPositive();
        assertThat(snap.powerKw()).isLessThan(7.4 * 1.10);
    }

    @Test
    void energyAccumulatesMonotonically() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        PowerSnapshot s1 = profile.tick(Duration.ofSeconds(10));
        PowerSnapshot s2 = profile.tick(Duration.ofSeconds(10));
        assertThat(s2.totalEnergyKwh()).isGreaterThan(s1.totalEnergyKwh());
    }

    @Test
    void socAdvancesWithTime() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        profile.tick(Duration.ofSeconds(60));
        double socAfter1Min = profile.getSocPercent();
        profile.tick(Duration.ofSeconds(60));
        assertThat(profile.getSocPercent()).isGreaterThan(socAfter1Min);
    }

    @Test
    void ccPlateauAtMaxPower() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        for (int i = 0; i < 60; i++) {
            profile.tick(Duration.ofSeconds(10));
        }
        assertThat(profile.getSocPercent()).isBetween(5.0, 80.0);

        PowerSnapshot snap = profile.tick(Duration.ofSeconds(1));
        assertThat(snap.powerKw()).isBetween(7.4 * 0.92, 7.4 * 1.08);
    }

    @Test
    void cvPhaseDecreasesPower() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        while (profile.getSocPercent() < 90.0) {
            profile.tick(Duration.ofSeconds(10));
        }
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(1));
        assertThat(snap.powerKw()).isLessThan(7.4);
    }

    @Test
    void completesAt100Percent() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        while (!profile.isComplete()) {
            profile.tick(Duration.ofSeconds(10));
        }
        assertThat(profile.getSocPercent()).isEqualTo(100.0);
        assertThat(profile.isComplete()).isTrue();
    }

    @Test
    void voltageIsAroundNominal() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(10));
        assertThat(snap.voltage()).isBetween(230.0, 235.0);
    }

    @Test
    void currentIsPowerOverVoltage() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(60));
        double expected = (snap.powerKw() * 1000.0) / snap.voltage();
        assertThat(snap.currentAmps()).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void temperatureScalesWithPower() {
        ChargingProfile profile = new ChargingProfile(7.4, 15, new Random(DETERMINISTIC_SEED));
        while (profile.getSocPercent() < 50.0) {
            profile.tick(Duration.ofSeconds(10));
        }
        PowerSnapshot snap = profile.tick(Duration.ofSeconds(1));
        assertThat(snap.temperatureCelsius()).isBetween(20.0, 50.0);
    }
}
