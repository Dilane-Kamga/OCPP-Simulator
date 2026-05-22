package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties.ChargePointConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChargePointSimulatorStateMachineTest {

    private static final long DETERMINISTIC_SEED = 42L;

    private OcppClient client;
    private ChargePointSimulator simulator;
    private SimulatorProperties props;
    private ChargePointConfig config;

    @BeforeEach
    void setUp() {
        client = mock(OcppClient.class);
        when(client.connect()).thenReturn(true);
        when(client.isConnected()).thenReturn(true);
        when(client.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        config = new ChargePointConfig("BORNE_TEST", "Legrand", "Green'Up Premium",
                "LGR-TEST", 7.4, 1, "1.4.2");
        props = new SimulatorProperties(true, 15, 30, 10, 0.0, 0.0,
                List.of(config), List.of("RFID-TEST"));

        simulator = new ChargePointSimulator(config, props, client, new Random(DETERMINISTIC_SEED));
    }

    @Test
    void initialStateIsBooting() {
        assertThat(simulator.getState()).isEqualTo(SimulatorState.BOOTING);
    }

    @Test
    void bootTransitionsToAvailable() {
        simulator.boot();
        assertThat(simulator.getState()).isEqualTo(SimulatorState.AVAILABLE);
    }

    @Test
    void startSessionFromAvailableTransitionsToPreparing() {
        simulator.boot();
        simulator.startSession(1, "RFID-TEST");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.PREPARING);
    }

    @Test
    void confirmCableTransitionsToCharging() {
        simulator.boot();
        simulator.startSession(1, "RFID-TEST");
        simulator.confirmCablePluggedAndStartCharging(5001);
        assertThat(simulator.getState()).isEqualTo(SimulatorState.CHARGING);
        assertThat(simulator.getCurrentTransactionId()).isEqualTo(5001);
    }

    @Test
    void stopSessionFromChargingReturnsToAvailable() {
        simulator.boot();
        simulator.startSession(1, "RFID-TEST");
        simulator.confirmCablePluggedAndStartCharging(5001);
        simulator.stopSession("Local");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.AVAILABLE);
        assertThat(simulator.getCurrentTransactionId()).isNull();
    }

    @Test
    void faultFromAnyStateTransitionsToFaulted() {
        simulator.boot();
        simulator.fault("GroundFailure");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.FAULTED);
    }

    @Test
    void recoverFromFaultedReturnsToAvailable() {
        simulator.boot();
        simulator.fault("GroundFailure");
        simulator.recoverFromFault();
        assertThat(simulator.getState()).isEqualTo(SimulatorState.AVAILABLE);
    }

    @Test
    void resetReturnsToBooting() {
        simulator.boot();
        simulator.reset();
        assertThat(simulator.getState()).isEqualTo(SimulatorState.BOOTING);
    }

    @Test
    void cannotStartSessionWhenNotAvailable() {
        // BOOTING state — startSession is a no-op
        simulator.startSession(1, "RFID-TEST");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.BOOTING);
    }

    @Test
    void cannotStopSessionWhenNotCharging() {
        simulator.boot();
        // AVAILABLE state — stopSession is a no-op
        simulator.stopSession("Local");
        assertThat(simulator.getState()).isEqualTo(SimulatorState.AVAILABLE);
    }

    @Test
    void bootSendsBootNotificationAndStatusNotifications() {
        simulator.boot();
        // 1 BootNotification + 1 StatusNotification(connector=0) + 1 StatusNotification per connector (config has 1)
        verify(client, times(3)).send(any());
    }
}
