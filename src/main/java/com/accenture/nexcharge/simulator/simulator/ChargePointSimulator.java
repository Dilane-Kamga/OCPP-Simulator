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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * State machine for a single simulated charge point.
 *
 * <p>Transitions: {@code BOOTING -> AVAILABLE -> PREPARING -> CHARGING -> AVAILABLE}
 * with {@code FAULTED} reachable from any state and recoverable to {@code AVAILABLE}.
 *
 * <p>All public mutating methods are {@code synchronized} to make the transitions atomic
 * w.r.t. concurrent ticks from the {@code SimulatorManager} scheduler. {@link #getState()}
 * uses an {@link AtomicReference} for lock-free reads.
 */
@Slf4j
public class ChargePointSimulator {

    /** Default connector identifier when none was assigned (e.g. for fault before any session). */
    private static final int DEFAULT_FAULT_CONNECTOR_ID = 1;

    private final ChargePointConfig config;
    private final SimulatorProperties properties;
    private final OcppClient client;
    private final Random random;
    private final ChargingProfile chargingProfile;

    private final AtomicReference<SimulatorState> state = new AtomicReference<>(SimulatorState.BOOTING);

    @Getter
    private volatile Integer currentTransactionId;
    @Getter
    private volatile Integer currentConnectorId;
    @Getter
    private volatile String currentIdTag;
    @Getter
    private volatile double meterStartWh;
    @Getter
    private volatile double currentMeterWh;

    public ChargePointSimulator(ChargePointConfig config, SimulatorProperties properties,
                                OcppClient client, Random random) {
        this.config = config;
        this.properties = properties;
        this.client = client;
        this.random = random;
        this.chargingProfile = new ChargingProfile(
                config.maxPowerKw(), properties.accelerationFactor(), random);
    }

    public ChargePointConfig getConfig() {
        return config;
    }

    public SimulatorState getState() {
        return state.get();
    }

    public synchronized void boot() {
        if (state.get() != SimulatorState.BOOTING) {
            return;
        }
        sendBootNotification();
        sendStatusNotification(0, ChargePointStatus.Available);
        for (int connectorId = 1; connectorId <= config.connectors(); connectorId++) {
            sendStatusNotification(connectorId, ChargePointStatus.Available);
        }
        state.set(SimulatorState.AVAILABLE);
        log.info("[{}] Booted - Status: Available", config.id());
    }

    public synchronized void startSession(int connectorId, String idTag) {
        if (state.get() != SimulatorState.AVAILABLE) {
            log.debug("[{}] startSession ignored, current state {}", config.id(), state.get());
            return;
        }
        sendAuthorize(idTag);
        sendStatusNotification(connectorId, ChargePointStatus.Preparing);
        currentConnectorId = connectorId;
        currentIdTag = idTag;
        state.set(SimulatorState.PREPARING);
        log.info("[{}] Preparing - connector {} idTag {}", config.id(), connectorId, idTag);
    }

    public synchronized void confirmCablePluggedAndStartCharging(int assignedTransactionId) {
        if (state.get() != SimulatorState.PREPARING) {
            return;
        }
        currentTransactionId = assignedTransactionId;
        meterStartWh = currentMeterWh;
        if (currentConnectorId != null) {
            sendStatusNotification(currentConnectorId, ChargePointStatus.Charging);
        }
        state.set(SimulatorState.CHARGING);
        log.info("[{}] Charging - txn {}", config.id(), assignedTransactionId);
    }

    public synchronized void stopSession(String reason) {
        if (state.get() != SimulatorState.CHARGING) {
            return;
        }
        sendStopTransaction(reason);
        if (currentConnectorId != null) {
            sendStatusNotification(currentConnectorId, ChargePointStatus.Available);
        }
        currentTransactionId = null;
        currentIdTag = null;
        currentConnectorId = null;
        state.set(SimulatorState.AVAILABLE);
        log.info("[{}] Stopped - reason {}", config.id(), reason);
    }

    public synchronized void fault(String errorCode) {
        if (state.get() == SimulatorState.CHARGING) {
            stopSession("Other");
        }
        int connectorId = currentConnectorId != null ? currentConnectorId : DEFAULT_FAULT_CONNECTOR_ID;
        sendStatusNotificationWithError(connectorId, ChargePointStatus.Faulted, errorCode);
        state.set(SimulatorState.FAULTED);
        log.warn("[{}] FAULTED - {}", config.id(), errorCode);
    }

    public synchronized void recoverFromFault() {
        if (state.get() != SimulatorState.FAULTED) {
            return;
        }
        int connectorId = currentConnectorId != null ? currentConnectorId : DEFAULT_FAULT_CONNECTOR_ID;
        sendStatusNotificationWithError(connectorId, ChargePointStatus.Available, "NoError");
        state.set(SimulatorState.AVAILABLE);
        log.info("[{}] Recovered from fault", config.id());
    }

    public synchronized void reset() {
        if (state.get() == SimulatorState.CHARGING) {
            stopSession("Reset");
        }
        currentTransactionId = null;
        currentIdTag = null;
        currentConnectorId = null;
        state.set(SimulatorState.BOOTING);
        log.info("[{}] Reset - state BOOTING", config.id());
    }

    /**
     * Called every {@code meter-interval-seconds} of real time while in CHARGING.
     * Advances the {@link ChargingProfile}, updates the cumulative meter, sends MeterValues,
     * and auto-stops the session once the profile reports 100% SoC.
     *
     * @param realElapsed real-time elapsed since the previous tick
     * @return the latest {@link PowerSnapshot}, or {@code null} if not in CHARGING
     */
    public synchronized PowerSnapshot tickMeter(Duration realElapsed) {
        if (state.get() != SimulatorState.CHARGING) {
            return null;
        }
        PowerSnapshot snap = chargingProfile.tick(realElapsed);
        currentMeterWh = meterStartWh + snap.totalEnergyKwh() * 1000.0;
        sendMeterValues(snap);
        if (chargingProfile.isComplete()) {
            stopSession("Local");
        }
        return snap;
    }

    public synchronized void sendHeartbeat() {
        if (!client.isConnected()) {
            return;
        }
        client.send(new HeartbeatRequest());
    }

    public synchronized void triggerStatusNotification(int connectorId) {
        ChargePointStatus status = mapStateToStatus();
        sendStatusNotification(connectorId, status);
    }

    public synchronized void triggerBootNotification() {
        sendBootNotification();
    }

    private ChargePointStatus mapStateToStatus() {
        return switch (state.get()) {
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
     * Send a StartTransaction. Used by the manager once it has obtained the assigned
     * transactionId from the CSMS confirmation. The simulator tracks the meter locally
     * so {@code currentMeterWh} reflects the start value.
     */
    public synchronized void sendStartTransaction(int connectorId, String idTag) {
        StartTransactionRequest req = new StartTransactionRequest(
                connectorId, idTag, (int) currentMeterWh, ZonedDateTime.now());
        client.send(req);
    }

    private void sendStopTransaction(String reason) {
        if (currentTransactionId == null) {
            return;
        }
        StopTransactionRequest req = new StopTransactionRequest(
                (int) currentMeterWh, ZonedDateTime.now(), currentTransactionId);
        try {
            req.setReason(Reason.valueOf(reason));
        } catch (IllegalArgumentException ignored) {
            req.setReason(Reason.Local);
        }
        client.send(req);
    }

    private void sendMeterValues(PowerSnapshot snap) {
        if (currentConnectorId == null) {
            return;
        }
        MeterValuesRequest req = new MeterValuesRequest(currentConnectorId);
        if (currentTransactionId != null) {
            req.setTransactionId(currentTransactionId);
        }
        ZonedDateTime ts = ZonedDateTime.now();
        req.setMeterValue(new MeterValue[]{ buildMeterValue(ts, snap) });
        client.send(req);
    }

    private MeterValue buildMeterValue(ZonedDateTime ts, PowerSnapshot snap) {
        MeterValue mv = new MeterValue();
        mv.setTimestamp(ts);
        mv.setSampledValue(new SampledValue[]{
                sampledValue(String.valueOf(currentMeterWh), "Energy.Active.Import.Register", "Wh"),
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
}
