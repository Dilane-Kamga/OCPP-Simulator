package com.accenture.nexcharge.simulator.simulator;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties.ChargePointConfig;
import eu.chargetime.ocpp.model.core.AuthorizeRequest;
import eu.chargetime.ocpp.model.core.BootNotificationRequest;
import eu.chargetime.ocpp.model.core.ChargePointErrorCode;
import eu.chargetime.ocpp.model.core.ChargePointStatus;
import eu.chargetime.ocpp.model.core.HeartbeatRequest;
import eu.chargetime.ocpp.model.core.MeterValue;
import eu.chargetime.ocpp.model.core.MeterValuesRequest;
import eu.chargetime.ocpp.model.core.Reason;
import eu.chargetime.ocpp.model.core.SampledValue;
import eu.chargetime.ocpp.model.core.StartTransactionRequest;
import eu.chargetime.ocpp.model.core.StatusNotificationRequest;
import eu.chargetime.ocpp.model.core.StopTransactionRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * State machine for a simulated charge point with per-connector concurrent sessions.
 *
 * <p>Each physical connector tracks its own {@link ConnectorSession} (transaction id, meter,
 * charging profile, lifecycle state). The borne-level {@link #getState()} is derived from
 * connector states for back-compat: CHARGING if any connector is charging, PREPARING if any
 * is preparing, FAULTED while a fault is latched, BOOTING before boot, otherwise AVAILABLE.
 *
 * <p>All public mutating methods are {@code synchronized} on this instance to keep the
 * connector map consistent against concurrent ticks from {@code SimulatorManager}.
 */
@Slf4j
public class ChargePointSimulator {

    /** Default connector identifier when none was assigned (e.g. for fault before any session). */
    private static final int DEFAULT_FAULT_CONNECTOR_ID = 1;

    private final ChargePointConfig config;
    private final SimulatorProperties properties;
    private final OcppClient client;
    private final Random random;

    private final AtomicReference<SimulatorState> borneState = new AtomicReference<>(SimulatorState.BOOTING);
    private final Map<Integer, ConnectorSession> sessions = new ConcurrentHashMap<>();
    private final java.util.Set<Integer> faultedConnectors = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ChargePointSimulator(ChargePointConfig config, SimulatorProperties properties,
                                OcppClient client, Random random) {
        this.config = config;
        this.properties = properties;
        this.client = client;
        this.random = random;
    }

    public ChargePointConfig getConfig() {
        return config;
    }

    /**
     * Coarse borne-level state derived from connector sessions.
     * CHARGING wins over PREPARING, which wins over AVAILABLE. FAULTED is latched separately.
     */
    public SimulatorState getState() {
        SimulatorState s = borneState.get();
        if (s == SimulatorState.BOOTING || s == SimulatorState.FAULTED) {
            return s;
        }
        if (faultedConnectors.size() >= config.connectors()) {
            return SimulatorState.FAULTED;
        }
        boolean anyCharging = false;
        boolean anyPreparing = false;
        for (ConnectorSession cs : sessions.values()) {
            if (cs.state == SimulatorState.CHARGING) {
                anyCharging = true;
            } else if (cs.state == SimulatorState.PREPARING) {
                anyPreparing = true;
            }
        }
        if (anyCharging) return SimulatorState.CHARGING;
        if (anyPreparing) return SimulatorState.PREPARING;
        return SimulatorState.AVAILABLE;
    }

    /** State of a specific physical connector. */
    public SimulatorState getConnectorState(int connectorId) {
        if (borneState.get() == SimulatorState.BOOTING) return SimulatorState.BOOTING;
        if (borneState.get() == SimulatorState.FAULTED) return SimulatorState.FAULTED;
        if (faultedConnectors.contains(connectorId)) return SimulatorState.FAULTED;
        ConnectorSession cs = sessions.get(connectorId);
        return cs == null ? SimulatorState.AVAILABLE : cs.state;
    }

    public boolean isConnectorAvailable(int connectorId) {
        return getConnectorState(connectorId) == SimulatorState.AVAILABLE;
    }

    /** First active session's transaction id (for back-compat with single-session callers). */
    public Integer getCurrentTransactionId() {
        ConnectorSession s = anyActive();
        return s == null ? null : s.transactionId;
    }

    public Integer getCurrentConnectorId() {
        ConnectorSession s = anyActive();
        return s == null ? null : s.connectorId;
    }

    public String getCurrentIdTag() {
        ConnectorSession s = anyActive();
        return s == null ? null : s.idTag;
    }

    public double getMeterStartWh() {
        ConnectorSession s = anyActive();
        return s == null ? 0.0 : s.meterStartWh;
    }

    public double getCurrentMeterWh() {
        ConnectorSession s = anyActive();
        return s == null ? 0.0 : s.currentMeterWh;
    }

    public Integer getTransactionId(int connectorId) {
        ConnectorSession s = sessions.get(connectorId);
        return s == null ? null : s.transactionId;
    }

    private ConnectorSession anyActive() {
        for (ConnectorSession s : sessions.values()) {
            if (s.state == SimulatorState.CHARGING || s.state == SimulatorState.PREPARING) {
                return s;
            }
        }
        return null;
    }

    public synchronized void boot() {
        if (borneState.get() != SimulatorState.BOOTING) {
            return;
        }
        sendBootNotification();
        sendStatusNotification(0, ChargePointStatus.Available);
        for (int connectorId = 1; connectorId <= config.connectors(); connectorId++) {
            sendStatusNotification(connectorId, ChargePointStatus.Available);
        }
        borneState.set(SimulatorState.AVAILABLE);
        sessions.clear();
        log.info("[{}] Booted - Status: Available", config.id());
    }

    public synchronized void startSession(int connectorId, String idTag) {
        SimulatorState borne = borneState.get();
        if (borne == SimulatorState.BOOTING || borne == SimulatorState.FAULTED) {
            log.debug("[{}] startSession ignored, borne state {}", config.id(), borne);
            return;
        }
        if (sessions.containsKey(connectorId)) {
            log.debug("[{}] startSession ignored, connector {} busy", config.id(), connectorId);
            return;
        }
        sendAuthorize(idTag);
        sendStatusNotification(connectorId, ChargePointStatus.Preparing);
        ConnectorSession cs = new ConnectorSession(connectorId, idTag,
                new ChargingProfile(config.maxPowerKw(), properties.accelerationFactor(), random));
        cs.state = SimulatorState.PREPARING;
        sessions.put(connectorId, cs);
        log.info("[{}] Preparing - connector {} idTag {}", config.id(), connectorId, idTag);
    }

    public synchronized void confirmCablePluggedAndStartCharging(int connectorId, int assignedTransactionId) {
        ConnectorSession cs = sessions.get(connectorId);
        if (cs == null || cs.state != SimulatorState.PREPARING) {
            return;
        }
        cs.transactionId = assignedTransactionId;
        cs.meterStartWh = cs.currentMeterWh;
        cs.state = SimulatorState.CHARGING;
        sendStatusNotification(connectorId, ChargePointStatus.Charging);
        log.info("[{}] Charging - connector {} txn {}", config.id(), connectorId, assignedTransactionId);
    }

    /** Back-compat: pick the only PREPARING session (single-connector borne flows). */
    public synchronized void confirmCablePluggedAndStartCharging(int assignedTransactionId) {
        ConnectorSession preparing = null;
        for (ConnectorSession s : sessions.values()) {
            if (s.state == SimulatorState.PREPARING) {
                preparing = s;
                break;
            }
        }
        if (preparing != null) {
            confirmCablePluggedAndStartCharging(preparing.connectorId, assignedTransactionId);
        }
    }

    public synchronized void stopSession(int connectorId, String reason) {
        ConnectorSession cs = sessions.get(connectorId);
        if (cs == null || cs.state != SimulatorState.CHARGING) {
            return;
        }
        sendStopTransaction(cs, reason);
        sendStatusNotification(connectorId, ChargePointStatus.Available);
        sessions.remove(connectorId);
        log.info("[{}] Stopped - connector {} reason {}", config.id(), connectorId, reason);
    }

    /** Back-compat: stop the first CHARGING connector. */
    public synchronized void stopSession(String reason) {
        ConnectorSession charging = null;
        for (ConnectorSession s : sessions.values()) {
            if (s.state == SimulatorState.CHARGING) {
                charging = s;
                break;
            }
        }
        if (charging != null) {
            stopSession(charging.connectorId, reason);
        }
    }

    /** Stop session by transaction id (used by RemoteStop routing). */
    public synchronized boolean stopSessionByTransactionId(int transactionId, String reason) {
        for (ConnectorSession s : sessions.values()) {
            if (s.transactionId != null && s.transactionId == transactionId
                    && s.state == SimulatorState.CHARGING) {
                stopSession(s.connectorId, reason);
                return true;
            }
        }
        return false;
    }

    /**
     * Fault a single connector. Stops only that connector's session and emits a Faulted
     * StatusNotification for that connectorId. The borne stays operational on its other
     * connectors. {@link #faultedConnectors} tracks faulted connectors so they refuse
     * new sessions until {@link #recoverFromFault()}.
     */
    public synchronized void fault(int connectorId, String errorCode) {
        ConnectorSession cs = sessions.get(connectorId);
        if (cs != null && cs.state == SimulatorState.CHARGING) {
            sendStopTransaction(cs, "Other");
        }
        sessions.remove(connectorId);
        faultedConnectors.add(connectorId);
        sendStatusNotificationWithError(connectorId, ChargePointStatus.Faulted, errorCode);
        log.warn("[{}] connector {} FAULTED - {}", config.id(), connectorId, errorCode);
    }

    /** Back-compat: fault the whole borne (used by scenario service "FAULT_ONE"). */
    public synchronized void fault(String errorCode) {
        fault(DEFAULT_FAULT_CONNECTOR_ID, errorCode);
    }

    public synchronized void recoverFromFault() {
        if (borneState.get() == SimulatorState.FAULTED) {
            for (int connectorId = 1; connectorId <= config.connectors(); connectorId++) {
                sendStatusNotificationWithError(connectorId, ChargePointStatus.Available, "NoError");
            }
            borneState.set(SimulatorState.AVAILABLE);
            faultedConnectors.clear();
            log.info("[{}] Recovered from fault", config.id());
            return;
        }
        if (!faultedConnectors.isEmpty()) {
            for (Integer connectorId : faultedConnectors) {
                sendStatusNotificationWithError(connectorId, ChargePointStatus.Available, "NoError");
            }
            log.info("[{}] Recovered connectors {} from fault", config.id(), faultedConnectors);
            faultedConnectors.clear();
        }
    }

    public synchronized void recoverConnectorFromFault(int connectorId) {
        if (faultedConnectors.remove(connectorId)) {
            sendStatusNotificationWithError(connectorId, ChargePointStatus.Available, "NoError");
            log.info("[{}] connector {} recovered from fault", config.id(), connectorId);
        }
    }

    public synchronized void reset() {
        for (ConnectorSession s : sessions.values()) {
            if (s.state == SimulatorState.CHARGING) {
                sendStopTransaction(s, "Reset");
            }
        }
        sessions.clear();
        borneState.set(SimulatorState.BOOTING);
        log.info("[{}] Reset - state BOOTING", config.id());
    }

    /**
     * Tick every active CHARGING connector. Returns the snapshot of any one of them
     * (back-compat for callers that expect a single value), or {@code null} if none charging.
     */
    public synchronized PowerSnapshot tickMeter(Duration realElapsed) {
        int activeCharging = 0;
        for (ConnectorSession cs : sessions.values()) {
            if (cs.state == SimulatorState.CHARGING) {
                activeCharging++;
            }
        }
        double perConnectorBudgetKw = activeCharging > 0
                ? config.maxPowerKw() / activeCharging
                : config.maxPowerKw();

        PowerSnapshot last = null;
        for (ConnectorSession cs : sessions.values()) {
            if (cs.state == SimulatorState.CHARGING) {
                PowerSnapshot snap = cs.profile.tick(realElapsed, perConnectorBudgetKw);
                cs.currentMeterWh = cs.meterStartWh + snap.totalEnergyKwh() * 1000.0;
                sendMeterValues(cs, snap);
                last = snap;
                if (cs.profile.isComplete()) {
                    stopSession(cs.connectorId, "Local");
                }
            }
        }
        return last;
    }

    public synchronized void sendHeartbeat() {
        if (!client.isConnected()) {
            return;
        }
        client.send(new HeartbeatRequest());
    }

    public synchronized void triggerStatusNotification(int connectorId) {
        ChargePointStatus status = mapConnectorToStatus(connectorId);
        sendStatusNotification(connectorId, status);
    }

    public synchronized void triggerBootNotification() {
        sendBootNotification();
    }

    private ChargePointStatus mapConnectorToStatus(int connectorId) {
        SimulatorState s = getConnectorState(connectorId);
        return switch (s) {
            case BOOTING, AVAILABLE -> ChargePointStatus.Available;
            case PREPARING -> ChargePointStatus.Preparing;
            case CHARGING -> ChargePointStatus.Charging;
            case FAULTED -> ChargePointStatus.Faulted;
        };
    }

    private void sendBootNotification() {
        BootNotificationRequest req = new BootNotificationRequest(config.vendor(), config.model());
        req.setChargePointSerialNumber(config.serial());
        req.setFirmwareVersion(config.firmware());
        client.send(req);
    }

    private void sendAuthorize(String idTag) {
        client.send(new AuthorizeRequest(idTag));
    }

    private void sendStatusNotification(int connectorId, ChargePointStatus status) {
        StatusNotificationRequest req = new StatusNotificationRequest(
                connectorId, ChargePointErrorCode.NoError, status);
        req.setTimestamp(ZonedDateTime.now());
        client.send(req);
    }

    private void sendStatusNotificationWithError(int connectorId, ChargePointStatus status, String errorCode) {
        ChargePointErrorCode code;
        try {
            code = ChargePointErrorCode.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            code = ChargePointErrorCode.NoError;
        }
        StatusNotificationRequest req = new StatusNotificationRequest(connectorId, code, status);
        req.setTimestamp(ZonedDateTime.now());
        client.send(req);
    }

    /**
     * Send a StartTransaction for the given pending connector and return the future of
     * the CSMS confirmation.
     */
    public synchronized CompletableFuture<?> sendStartTransactionAndAwait(int connectorId) {
        ConnectorSession cs = sessions.get(connectorId);
        if (cs == null || cs.state != SimulatorState.PREPARING) {
            CompletableFuture<Integer> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("No pending session on connector " + connectorId));
            return failed;
        }
        StartTransactionRequest req = new StartTransactionRequest(
                cs.connectorId, cs.idTag, (int) cs.currentMeterWh, ZonedDateTime.now());
        return client.send(req);
    }

    /** Back-compat: send StartTransaction for the only PREPARING connector. */
    public synchronized CompletableFuture<?> sendStartTransactionAndAwait() {
        for (ConnectorSession cs : sessions.values()) {
            if (cs.state == SimulatorState.PREPARING) {
                return sendStartTransactionAndAwait(cs.connectorId);
            }
        }
        CompletableFuture<Integer> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("No pending session"));
        return failed;
    }

    private void sendStopTransaction(ConnectorSession cs, String reason) {
        if (cs.transactionId == null) {
            return;
        }
        StopTransactionRequest req = new StopTransactionRequest(
                (int) cs.currentMeterWh, ZonedDateTime.now(), cs.transactionId);
        try {
            req.setReason(Reason.valueOf(reason));
        } catch (IllegalArgumentException ignored) {
            req.setReason(Reason.Local);
        }
        client.send(req);
    }

    private void sendMeterValues(ConnectorSession cs, PowerSnapshot snap) {
        MeterValuesRequest req = new MeterValuesRequest(cs.connectorId);
        if (cs.transactionId != null) {
            req.setTransactionId(cs.transactionId);
        }
        ZonedDateTime ts = ZonedDateTime.now();
        req.setMeterValue(new MeterValue[]{ buildMeterValue(ts, cs, snap) });
        client.send(req);
    }

    private MeterValue buildMeterValue(ZonedDateTime ts, ConnectorSession cs, PowerSnapshot snap) {
        MeterValue mv = new MeterValue();
        mv.setTimestamp(ts);
        mv.setSampledValue(new SampledValue[]{
                sampledValue(String.valueOf(cs.currentMeterWh), "Energy.Active.Import.Register", "Wh"),
                sampledValue(String.format(Locale.ROOT, "%.1f", snap.powerKw() * 1000.0), "Power.Active.Import", "W"),
                sampledValue(String.format(Locale.ROOT, "%.2f", snap.currentAmps()), "Current.Import", "A"),
                sampledValue(String.format(Locale.ROOT, "%.1f", snap.voltage()), "Voltage", "V"),
                sampledValue(String.format(Locale.ROOT, "%.1f", snap.temperatureCelsius()), "Temperature", "Celsius")
        });
        return mv;
    }

    private SampledValue sampledValue(String value, String measurand, String unit) {
        SampledValue sv = new SampledValue();
        sv.setValue(value);
        sv.setMeasurand(measurand);
        sv.setUnit(unit);
        return sv;
    }

    /** Per-connector session state held by the simulator. */
    private static final class ConnectorSession {
        final int connectorId;
        final String idTag;
        final ChargingProfile profile;
        volatile SimulatorState state = SimulatorState.AVAILABLE;
        volatile Integer transactionId;
        volatile double meterStartWh;
        volatile double currentMeterWh;

        ConnectorSession(int connectorId, String idTag, ChargingProfile profile) {
            this.connectorId = connectorId;
            this.idTag = idTag;
            this.profile = profile;
        }
    }
}
