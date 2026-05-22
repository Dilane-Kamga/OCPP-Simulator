package com.accenture.nexcharge.simulator.ocpp;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OcppSessionRegistryTest {

    @Test
    void registerAndLookup() {
        OcppSessionRegistry registry = new OcppSessionRegistry();
        UUID sessionId = UUID.randomUUID();
        registry.register("BORNE_A", sessionId);

        assertThat(registry.findSessionId("BORNE_A")).hasValue(sessionId);
        assertThat(registry.findChargePointId(sessionId)).hasValue("BORNE_A");
    }

    @Test
    void unregisterRemovesBothMappings() {
        OcppSessionRegistry registry = new OcppSessionRegistry();
        UUID sessionId = UUID.randomUUID();
        registry.register("BORNE_A", sessionId);
        registry.unregisterBySessionId(sessionId);

        assertThat(registry.findSessionId("BORNE_A")).isEmpty();
        assertThat(registry.findChargePointId(sessionId)).isEmpty();
    }

    @Test
    void replaceOldSessionWhenSameChargePointReconnects() {
        OcppSessionRegistry registry = new OcppSessionRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.register("BORNE_A", first);
        registry.register("BORNE_A", second);

        assertThat(registry.findSessionId("BORNE_A")).hasValue(second);
        assertThat(registry.findChargePointId(first)).isEmpty();
        assertThat(registry.findChargePointId(second)).hasValue("BORNE_A");
    }

    @Test
    void missingLookupsReturnEmpty() {
        OcppSessionRegistry registry = new OcppSessionRegistry();
        assertThat(registry.findSessionId("NOPE")).isEmpty();
        assertThat(registry.findChargePointId(UUID.randomUUID())).isEmpty();
    }
}
