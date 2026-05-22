package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.model.core.KeyValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChargePointConfigurationStoreTest {

    private ChargePointConfigurationStore store;

    @BeforeEach
    void setUp() {
        // Use the package-private constructor with explicit defaults
        store = new ChargePointConfigurationStore(30, 10);
    }

    // ── get all ───────────────────────────────────────────────────────────────

    @Test
    void getAllKeysReturnsBothSupportedKeys() {
        List<KeyValueType> result = store.get(null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(KeyValueType::getKey)
                .containsExactlyInAnyOrder(
                        ChargePointConfigurationStore.KEY_HEARTBEAT_INTERVAL,
                        ChargePointConfigurationStore.KEY_METER_VALUE_SAMPLE_INTERVAL);
    }

    @Test
    void getWithEmptyListReturnsBothSupportedKeys() {
        List<KeyValueType> result = store.get(List.of());

        assertThat(result).hasSize(2);
    }

    @Test
    void defaultValuesArePrePopulated() {
        assertThat(store.getValue(ChargePointConfigurationStore.KEY_HEARTBEAT_INTERVAL))
                .isEqualTo("30");
        assertThat(store.getValue(ChargePointConfigurationStore.KEY_METER_VALUE_SAMPLE_INTERVAL))
                .isEqualTo("10");
    }

    // ── get subset ───────────────────────────────────────────────────────────

    @Test
    void getFiltersByRequestedKeys() {
        List<KeyValueType> result =
                store.get(List.of(ChargePointConfigurationStore.KEY_HEARTBEAT_INTERVAL));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKey())
                .isEqualTo(ChargePointConfigurationStore.KEY_HEARTBEAT_INTERVAL);
        assertThat(result.get(0).getValue()).isEqualTo("30");
    }

    @Test
    void getReturnsEmptyForFullyUnknownKeys() {
        List<KeyValueType> result = store.get(List.of("CompletelyUnknownKey"));

        assertThat(result).isEmpty();
    }

    @Test
    void getMixedKnownAndUnknownReturnsOnlyKnown() {
        List<KeyValueType> result = store.get(
                List.of(ChargePointConfigurationStore.KEY_HEARTBEAT_INTERVAL, "UnknownKey"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKey())
                .isEqualTo(ChargePointConfigurationStore.KEY_HEARTBEAT_INTERVAL);
    }

    // ── set ──────────────────────────────────────────────────────────────────

    @Test
    void setKnownKeyReturnsTrueAndUpdatesValue() {
        boolean result = store.set(ChargePointConfigurationStore.KEY_HEARTBEAT_INTERVAL, "60");

        assertThat(result).isTrue();
        assertThat(store.getValue(ChargePointConfigurationStore.KEY_HEARTBEAT_INTERVAL))
                .isEqualTo("60");
    }

    @Test
    void setUnknownKeyReturnsFalse() {
        boolean result = store.set("SomeVendorSpecificKey", "123");

        assertThat(result).isFalse();
    }

    @Test
    void setMeterIntervalUpdatesValue() {
        boolean result = store.set(
                ChargePointConfigurationStore.KEY_METER_VALUE_SAMPLE_INTERVAL, "30");

        assertThat(result).isTrue();
        assertThat(store.getValue(
                ChargePointConfigurationStore.KEY_METER_VALUE_SAMPLE_INTERVAL)).isEqualTo("30");
    }

    @Test
    void allKeyValuesAreReadonlyFalse() {
        List<KeyValueType> result = store.get(null);

        assertThat(result).allMatch(kv -> Boolean.FALSE.equals(kv.getReadonly()));
    }
}
