package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.dto.ScenarioRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * Manually-triggered scenarios for the OCPP simulator.
 *
 * <p>Backs the {@code POST /api/simulator/scenario} endpoint. Each scenario name maps to a
 * deterministic action against the {@link SimulatorManager}'s registered simulators:
 * <ul>
 *   <li>{@code START_ALL} — start a session on every AVAILABLE simulator</li>
 *   <li>{@code STOP_ALL} — stop every CHARGING session (reason {@code "Remote"})</li>
 *   <li>{@code FAULT_ONE} — fault a specific simulator if {@code chargePointId} is set,
 *       otherwise the first non-FAULTED one</li>
 *   <li>{@code DISCONNECT_ONE} — reset (reboot) a specific simulator</li>
 *   <li>{@code PEAK_LOAD} — start AVAILABLE, recover FAULTED, leave CHARGING/PREPARING alone</li>
 *   <li>{@code RESET_ALL} — reset every simulator</li>
 * </ul>
 *
 * <p>Throws {@link IllegalArgumentException} on unknown scenario.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulatorScenarioService {

    /** Default connector targeted by mass-start scenarios. */
    private static final int DEFAULT_CONNECTOR_ID = 1;
    /** Reason used when stopping sessions remotely via scenarios. */
    private static final String REMOTE_STOP_REASON = "Remote";
    /** Error code raised by FAULT_ONE / fault-injection scenarios. */
    private static final String DEFAULT_FAULT_ERROR_CODE = "GroundFailure";
    /** Fallback idTag when the configured rfid list is empty. */
    private static final String DEFAULT_RFID_TAG = "RFID-DEFAULT";

    private final SimulatorManager manager;
    private final SimulatorProperties properties;
    private final Random random = new Random();

    public void run(ScenarioRequest request) {
        String scenario = request.scenario();
        log.info("[SCENARIO] {} target={}", scenario, request.chargePointId());
        switch (scenario) {
            case "START_ALL" -> startAll();
            case "STOP_ALL" -> stopAll();
            case "FAULT_ONE" -> faultOne(request.chargePointId());
            case "DISCONNECT_ONE" -> disconnectOne(request.chargePointId());
            case "PEAK_LOAD" -> peakLoad();
            case "RESET_ALL" -> resetAll();
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }

    private void startAll() {
        for (ChargePointSimulator s : manager.getAll()) {
            if (s.getState() == SimulatorState.AVAILABLE) {
                manager.triggerSessionStart(s, DEFAULT_CONNECTOR_ID, pickRfid());
            }
        }
    }

    private void stopAll() {
        for (ChargePointSimulator s : manager.getAll()) {
            if (s.getState() == SimulatorState.CHARGING) {
                s.stopSession(REMOTE_STOP_REASON);
            }
        }
    }

    private void faultOne(String chargePointId) {
        ChargePointSimulator s = (chargePointId != null)
                ? manager.get(chargePointId)
                : manager.getAll().stream()
                    .filter(x -> x.getState() != SimulatorState.FAULTED)
                    .findFirst().orElse(null);
        if (s != null) {
            s.fault(DEFAULT_FAULT_ERROR_CODE);
        }
    }

    private void disconnectOne(String chargePointId) {
        ChargePointSimulator s = (chargePointId != null) ? manager.get(chargePointId) : null;
        if (s != null) {
            s.reset();
        }
    }

    private void peakLoad() {
        for (ChargePointSimulator s : manager.getAll()) {
            switch (s.getState()) {
                case AVAILABLE -> manager.triggerSessionStart(s, DEFAULT_CONNECTOR_ID, pickRfid());
                case FAULTED -> s.recoverFromFault();
                default -> { /* leave CHARGING and PREPARING as is */ }
            }
        }
    }

    private void resetAll() {
        for (ChargePointSimulator s : manager.getAll()) {
            s.reset();
        }
    }

    private String pickRfid() {
        List<String> tags = properties.rfidTags();
        return (tags == null || tags.isEmpty())
                ? DEFAULT_RFID_TAG
                : tags.get(random.nextInt(tags.size()));
    }
}
