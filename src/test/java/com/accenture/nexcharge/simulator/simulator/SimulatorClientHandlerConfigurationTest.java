package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.model.core.ChangeConfigurationConfirmation;
import eu.chargetime.ocpp.model.core.ChangeConfigurationRequest;
import eu.chargetime.ocpp.model.core.ConfigurationStatus;
import eu.chargetime.ocpp.model.core.GetConfigurationConfirmation;
import eu.chargetime.ocpp.model.core.GetConfigurationRequest;
import eu.chargetime.ocpp.model.core.KeyValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the ChangeConfiguration / GetConfiguration handling
 * in {@link SimulatorClientHandler} wired to a real
 * {@link ChargePointConfigurationStore}.
 */
class SimulatorClientHandlerConfigurationTest {

    private ChargePointConfigurationStore store;
    private SimulatorClientHandler handler;

    @BeforeEach
    void setUp() {
        store = new ChargePointConfigurationStore(30, 10);
        SimulatorClientHandler.InboundCommands commands =
                mock(SimulatorClientHandler.InboundCommands.class);
        handler = new SimulatorClientHandler("BORNE_X", commands, store);
    }

    // ── ChangeConfiguration ───────────────────────────────────────────────────

    @Test
    void changeConfigurationKnownKeyReturnsAccepted() {
        ChangeConfigurationRequest req =
                new ChangeConfigurationRequest("HeartbeatInterval", "60");

        ChangeConfigurationConfirmation conf =
                handler.handleChangeConfigurationRequest(req);

        assertThat(conf.getStatus()).isEqualTo(ConfigurationStatus.Accepted);
    }

    @Test
    void changeConfigurationKnownKeyMutatesStore() {
        ChangeConfigurationRequest req =
                new ChangeConfigurationRequest("HeartbeatInterval", "120");

        handler.handleChangeConfigurationRequest(req);

        assertThat(store.getValue("HeartbeatInterval")).isEqualTo("120");
    }

    @Test
    void changeConfigurationUnknownKeyReturnsRejected() {
        ChangeConfigurationRequest req =
                new ChangeConfigurationRequest("VendorSpecificKey", "xyz");

        ChangeConfigurationConfirmation conf =
                handler.handleChangeConfigurationRequest(req);

        assertThat(conf.getStatus()).isEqualTo(ConfigurationStatus.Rejected);
    }

    @Test
    void changeConfigurationMeterIntervalReturnsAccepted() {
        ChangeConfigurationRequest req =
                new ChangeConfigurationRequest("MeterValueSampleInterval", "30");

        ChangeConfigurationConfirmation conf =
                handler.handleChangeConfigurationRequest(req);

        assertThat(conf.getStatus()).isEqualTo(ConfigurationStatus.Accepted);
        assertThat(store.getValue("MeterValueSampleInterval")).isEqualTo("30");
    }

    // ── GetConfiguration ─────────────────────────────────────────────────────

    @Test
    void getConfigurationNullKeyReturnsAllKnownKeys() {
        GetConfigurationRequest req = new GetConfigurationRequest();
        // key array left null → fetch all

        GetConfigurationConfirmation conf = handler.handleGetConfigurationRequest(req);

        assertThat(conf.getConfigurationKey()).hasSize(2);
        assertThat(conf.getConfigurationKey())
                .extracting(KeyValueType::getKey)
                .containsExactlyInAnyOrder("HeartbeatInterval", "MeterValueSampleInterval");
        assertThat(conf.getUnknownKey()).isNull();
    }

    @Test
    void getConfigurationSpecificKnownKey() {
        GetConfigurationRequest req = new GetConfigurationRequest();
        req.setKey(new String[]{"HeartbeatInterval"});

        GetConfigurationConfirmation conf = handler.handleGetConfigurationRequest(req);

        assertThat(conf.getConfigurationKey()).hasSize(1);
        assertThat(conf.getConfigurationKey()[0].getKey()).isEqualTo("HeartbeatInterval");
        assertThat(conf.getConfigurationKey()[0].getValue()).isEqualTo("30");
    }

    @Test
    void getConfigurationUnknownKeyGoesInUnknownKeyList() {
        GetConfigurationRequest req = new GetConfigurationRequest();
        req.setKey(new String[]{"NoSuchKey"});

        GetConfigurationConfirmation conf = handler.handleGetConfigurationRequest(req);

        assertThat(conf.getConfigurationKey()).isEmpty();
        assertThat(conf.getUnknownKey()).containsExactly("NoSuchKey");
    }

    @Test
    void getConfigurationMixedKnownAndUnknown() {
        GetConfigurationRequest req = new GetConfigurationRequest();
        req.setKey(new String[]{"HeartbeatInterval", "UnknownKey"});

        GetConfigurationConfirmation conf = handler.handleGetConfigurationRequest(req);

        assertThat(conf.getConfigurationKey()).hasSize(1);
        assertThat(conf.getConfigurationKey()[0].getKey()).isEqualTo("HeartbeatInterval");
        assertThat(conf.getUnknownKey()).containsExactly("UnknownKey");
    }

    @Test
    void getConfigurationReturnsDefaultValuesFromStore() {
        GetConfigurationRequest req = new GetConfigurationRequest();

        GetConfigurationConfirmation conf = handler.handleGetConfigurationRequest(req);

        assertThat(conf.getConfigurationKey())
                .extracting(KeyValueType::getValue)
                .containsExactlyInAnyOrder("30", "10");
    }
}
