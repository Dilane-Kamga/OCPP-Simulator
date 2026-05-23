package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties.ChargePointConfig;
import com.accenture.nexcharge.simulator.model.dto.ScenarioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatorScenarioServiceTest {

    @Mock SimulatorManager manager;
    @Mock ChargePointSimulator s1;
    @Mock ChargePointSimulator s2;

    private SimulatorScenarioService service;
    private SimulatorProperties properties;

    @BeforeEach
    void setUp() {
        ChargePointConfig cfg = new ChargePointConfig(
                "BORNE_TEST", null, "Legrand", "Green'Up Premium", "LGR-TEST", 7.4, 1, "1.4.2");
        properties = new SimulatorProperties(true, 15, 30, 10, 0.0, 0.0,
                List.of(cfg), List.of("RFID-0001", "RFID-0002"));
        service = new SimulatorScenarioService(manager, properties);

        lenient().when(manager.getAll()).thenReturn(List.of(s1, s2));
        lenient().when(s1.getState()).thenReturn(SimulatorState.AVAILABLE);
        lenient().when(s2.getState()).thenReturn(SimulatorState.AVAILABLE);
    }

    @Test
    void startAllStartsAvailableOnes() {
        service.run(new ScenarioRequest("START_ALL", null));
        verify(manager).triggerSessionStart(eq(s1), eq(1), anyString());
        verify(manager).triggerSessionStart(eq(s2), eq(1), anyString());
    }

    @Test
    void stopAllStopsChargingOnes() {
        when(s1.getState()).thenReturn(SimulatorState.CHARGING);
        when(s2.getState()).thenReturn(SimulatorState.AVAILABLE);
        service.run(new ScenarioRequest("STOP_ALL", null));
        verify(s1).stopSession("Remote");
        verify(s2, never()).stopSession(anyString());
    }

    @Test
    void faultOneTargetsSpecificChargePoint() {
        when(manager.get("BORNE_A")).thenReturn(s1);
        service.run(new ScenarioRequest("FAULT_ONE", "BORNE_A"));
        verify(s1).fault(anyString());
    }

    @Test
    void resetAllResetsEverySimulator() {
        service.run(new ScenarioRequest("RESET_ALL", null));
        verify(s1).reset();
        verify(s2).reset();
    }

    @Test
    void disconnectOneResetsTargetSimulator() {
        when(manager.get("BORNE_A")).thenReturn(s1);
        service.run(new ScenarioRequest("DISCONNECT_ONE", "BORNE_A"));
        verify(s1).reset();
        verify(s2, never()).reset();
    }

    @Test
    void peakLoadStartsAvailableAndRecoversFaulted() {
        when(s1.getState()).thenReturn(SimulatorState.AVAILABLE);
        when(s2.getState()).thenReturn(SimulatorState.FAULTED);
        service.run(new ScenarioRequest("PEAK_LOAD", null));
        verify(manager).triggerSessionStart(eq(s1), eq(1), anyString());
        verify(s2).recoverFromFault();
    }

    @Test
    void unknownScenarioThrows() {
        assertThatThrownBy(() -> service.run(new ScenarioRequest("BOGUS", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
