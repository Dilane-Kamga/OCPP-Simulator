package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties.ChargePointConfig;
import com.accenture.nexcharge.simulator.ocpp.CsmsServer;
import com.accenture.nexcharge.simulator.ocpp.OcppSessionRegistry;
import com.accenture.nexcharge.simulator.simulator.SimulatorClientHandler.ConfigurationStore;
import com.accenture.nexcharge.simulator.simulator.SimulatorClientHandler.InboundCommands;
import eu.chargetime.ocpp.feature.profile.ClientCoreProfile;
import eu.chargetime.ocpp.feature.profile.ClientRemoteTriggerProfile;
import eu.chargetime.ocpp.model.core.StartTransactionConfirmation;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lifecycle owner for all simulated charge points.
 *
 * <p>On startup, creates one {@link ChargePointSimulator} per configured borne, each with its
 * own {@link JsonOcppClient}, and boots them sequentially with a {@value #BOOT_DELAY_MS} ms gap.
 * Three Spring-scheduled ticks then drive the simulation:
 * <ul>
 *   <li>{@link #heartbeatTick()} — every {@code heartbeat-interval-seconds} (real time)</li>
 *   <li>{@link #meterTick()} — every {@code meter-interval-seconds} for CHARGING simulators</li>
 *   <li>{@link #worldTick()} — probabilistic auto-sessions and faults</li>
 * </ul>
 *
 * <p>Inbound CSMS commands (RemoteStart/Stop, Reset, Unlock, TriggerMessage) are routed to the
 * corresponding simulator via the {@link InboundCommands} callback wired on the client profile.
 *
 * <p>Backed by an internal virtual-thread {@link ScheduledExecutorService} for boot delays and
 * post-PREPARING StartTransaction follow-ups; the @{@link Scheduled} ticks run on Spring's
 * default scheduler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimulatorManager {

    /** Real-time delay between boots so each charge point gets its own log block. */
    private static final long BOOT_DELAY_MS = 2000L;
    /** Real-time delay between PREPARING and CHARGING (simulating cable plug). */
    private static final long CABLE_PLUG_DELAY_SECONDS = 3L;
    /** Min real-time seconds before a FAULTED simulator auto-recovers. */
    private static final int FAULT_RECOVERY_MIN_SECONDS = 30;
    /** Random additional seconds on top of the min. Total range: 30-120s. */
    private static final int FAULT_RECOVERY_JITTER_SECONDS = 90;
    /** Default connector to use for auto-triggered sessions. */
    private static final int DEFAULT_AUTO_CONNECTOR_ID = 1;
    /** Fallback transaction id range when CSMS confirmation cannot be parsed. */
    private static final int FALLBACK_TXN_ID_BASE = 100_000;
    private static final int FALLBACK_TXN_ID_RANGE = 900_000;
    /** Fallback idTag when the configured rfid list is empty. */
    private static final String DEFAULT_RFID_TAG = "RFID-DEFAULT";

    private final SimulatorProperties properties;
    private final OcppProperties ocppProperties;
    @SuppressWarnings("unused") // kept for symmetry; lifecycle ordering ensures the CSMS is up before clients connect
    private final CsmsServer csmsServer;
    @SuppressWarnings("unused") // kept for future correlation between sessionId and chargePointId
    private final OcppSessionRegistry registry;

    private final Map<String, ChargePointSimulator> simulators = new ConcurrentHashMap<>();
    private final Map<String, JsonOcppClient> clients = new ConcurrentHashMap<>();
    private final Random globalRandom = new Random();
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        if (!properties.enabled()) {
            log.info("[SIMULATOR] disabled - not starting any simulated charge point");
            return;
        }
        scheduler = Executors.newScheduledThreadPool(2,
                Thread.ofVirtual().name("simulator-", 1).factory());

        log.info("[SIMULATOR] Starting {} simulated charge points...", properties.chargePoints().size());

        long delay = 0L;
        for (ChargePointConfig cfg : properties.chargePoints()) {
            scheduler.schedule(() -> bootOne(cfg), delay, TimeUnit.MILLISECONDS);
            delay += BOOT_DELAY_MS;
        }
    }

    private void bootOne(ChargePointConfig cfg) {
        String url = String.format("ws://localhost:%d", ocppProperties.server().port());
        ChargePointConfigurationStore configStore = new ChargePointConfigurationStore(properties);
        SimulatorClientHandler handler = new SimulatorClientHandler(
                cfg.id(), inboundCommandsFor(cfg.id()), configStore);
        ClientCoreProfile core = new ClientCoreProfile(handler);
        ClientRemoteTriggerProfile remoteTrigger = new ClientRemoteTriggerProfile(handler);
        JsonOcppClient client = new JsonOcppClient(cfg.id(), url, core, remoteTrigger);
        clients.put(cfg.id(), client);

        ChargePointSimulator sim = new ChargePointSimulator(
                cfg, properties, client, new Random(globalRandom.nextLong()));
        simulators.put(cfg.id(), sim);

        if (client.connect()) {
            sim.boot();
        } else {
            log.error("[{}] Failed to connect after retries", cfg.id());
        }
    }

    private InboundCommands inboundCommandsFor(String chargePointId) {
        return new InboundCommands() {
            @Override
            public void onRemoteStart(int connectorId, String idTag) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) {
                    s.startSession(connectorId, idTag);
                }
            }

            @Override
            public void onRemoteStop(int transactionId) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null && transactionId == nullSafe(s.getCurrentTransactionId())) {
                    s.stopSession("Remote");
                }
            }

            @Override
            public void onReset(boolean hard) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) {
                    s.reset();
                }
                JsonOcppClient c = clients.get(chargePointId);
                if (c != null) {
                    c.disconnect();
                    if (c.connect() && s != null) {
                        s.boot();
                    }
                }
            }

            @Override
            public void onUnlock(int connectorId) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null && s.getState() == SimulatorState.CHARGING) {
                    s.stopSession("UnlockCommand");
                }
            }

            @Override
            public void onTriggerStatusNotification(int connectorId) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) {
                    s.triggerStatusNotification(connectorId);
                }
            }

            @Override
            public void onTriggerHeartbeat() {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) {
                    s.sendHeartbeat();
                }
            }

            @Override
            public void onTriggerMeterValues(int connectorId) {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null && s.getState() == SimulatorState.CHARGING) {
                    s.tickMeter(Duration.ofSeconds(properties.meterIntervalSeconds()));
                }
            }

            @Override
            public void onTriggerBootNotification() {
                ChargePointSimulator s = simulators.get(chargePointId);
                if (s != null) {
                    s.triggerBootNotification();
                }
            }
        };
    }

    private int nullSafe(Integer v) {
        return v == null ? -1 : v;
    }

    @Scheduled(fixedDelayString = "${simulator.heartbeat-interval-seconds:30}000")
    public void heartbeatTick() {
        if (!properties.enabled()) {
            return;
        }
        for (ChargePointSimulator s : simulators.values()) {
            try {
                s.sendHeartbeat();
            } catch (Exception e) {
                log.warn("heartbeat error for {}: {}", s.getConfig().id(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${simulator.meter-interval-seconds:10}000")
    public void meterTick() {
        if (!properties.enabled()) {
            return;
        }
        Duration elapsed = Duration.ofSeconds(properties.meterIntervalSeconds());
        for (ChargePointSimulator s : simulators.values()) {
            try {
                if (s.getState() == SimulatorState.CHARGING) {
                    s.tickMeter(elapsed);
                }
            } catch (Exception e) {
                log.warn("meter tick error for {}: {}", s.getConfig().id(), e.getMessage());
            }
        }
    }

    /** Auto-trigger sessions and faults probabilistically. */
    @Scheduled(fixedDelayString = "${simulator.heartbeat-interval-seconds:30}000")
    public void worldTick() {
        if (!properties.enabled()) {
            return;
        }
        for (ChargePointSimulator s : simulators.values()) {
            try {
                if (s.getState() == SimulatorState.AVAILABLE
                        && globalRandom.nextDouble() < properties.autoSessionProbability()) {
                    String idTag = pickRandomRfid();
                    s.startSession(DEFAULT_AUTO_CONNECTOR_ID, idTag);
                    finishPreparing(s);
                } else if (s.getState() == SimulatorState.CHARGING
                        && globalRandom.nextDouble() < properties.randomEventProbability()) {
                    s.fault("GroundFailure");
                    scheduleRecovery(s);
                }
            } catch (Exception e) {
                log.warn("world tick error for {}: {}", s.getConfig().id(), e.getMessage());
            }
        }
    }

    private void finishPreparing(ChargePointSimulator s) {
        scheduler.schedule(() -> {
            int txnId = -1;
            try {
                Object confirmation = s.sendStartTransactionAndAwait().get();
                if (confirmation instanceof StartTransactionConfirmation stc
                        && stc.getTransactionId() != null) {
                    txnId = stc.getTransactionId();
                }
            } catch (Exception e) {
                log.debug("[{}] StartTransaction confirmation failed: {}",
                        s.getConfig().id(), e.getMessage());
            }
            if (txnId > 0) {
                s.confirmCablePluggedAndStartCharging(txnId);
            } else {
                s.confirmCablePluggedAndStartCharging(
                        globalRandom.nextInt(FALLBACK_TXN_ID_RANGE) + FALLBACK_TXN_ID_BASE);
            }
        }, CABLE_PLUG_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void scheduleRecovery(ChargePointSimulator s) {
        long delay = FAULT_RECOVERY_MIN_SECONDS + globalRandom.nextInt(FAULT_RECOVERY_JITTER_SECONDS);
        scheduler.schedule(s::recoverFromFault, delay, TimeUnit.SECONDS);
    }

    private String pickRandomRfid() {
        var tags = properties.rfidTags();
        return (tags == null || tags.isEmpty())
                ? DEFAULT_RFID_TAG
                : tags.get(globalRandom.nextInt(tags.size()));
    }

    public Collection<ChargePointSimulator> getAll() {
        return simulators.values();
    }

    public ChargePointSimulator get(String id) {
        return simulators.get(id);
    }

    /**
     * Start a session on the given simulator and schedule the PREPARING→CHARGING progression.
     * Used by manually-triggered paths (scenario API, RemoteStart) so they reach CHARGING
     * without waiting for the next {@link #worldTick()}.
     */
    public void triggerSessionStart(ChargePointSimulator s, int connectorId, String idTag) {
        if (s.getState() != SimulatorState.AVAILABLE) {
            return;
        }
        s.startSession(connectorId, idTag);
        finishPreparing(s);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        for (JsonOcppClient c : clients.values()) {
            try {
                c.disconnect();
            } catch (Exception ignored) {
                /* best-effort shutdown */
            }
        }
    }
}
