package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.JSONClient;
import eu.chargetime.ocpp.feature.profile.ClientCoreProfile;
import eu.chargetime.ocpp.feature.profile.ClientRemoteTriggerProfile;
import eu.chargetime.ocpp.model.Request;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link OcppClient} implementation backed by the SDK's {@link JSONClient}.
 *
 * <p>{@link #connect()} retries up to {@value #MAX_ATTEMPTS} times with exponential backoff
 * (starting at {@value #INITIAL_BACKOFF_MS} ms, doubling, capped at {@value #MAX_BACKOFF_MS} ms).
 * The Core profile is used at construction; the Remote Trigger profile is added before connecting
 * so the simulator can receive {@code TriggerMessage} requests from the CSMS.
 *
 * <p>Note: the SDK's {@code JSONClient.connect} is asynchronous — it returns immediately after
 * scheduling the connection. We mark {@code connected = true} once the call returns without
 * throwing; the {@link SimulatorManager} or test fixture should give the WebSocket a moment
 * to establish before sending requests. Errors during a later send are surfaced as a failed
 * {@link CompletableFuture}.
 */
@Slf4j
public class JsonOcppClient implements OcppClient {

    private static final long INITIAL_BACKOFF_MS = 2000L;
    private static final long MAX_BACKOFF_MS = 30000L;
    private static final int MAX_ATTEMPTS = 5;

    private final String chargePointId;
    private final String csmsUrl;
    private final ClientCoreProfile core;
    private final ClientRemoteTriggerProfile remoteTrigger;

    private JSONClient jsonClient;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public JsonOcppClient(String chargePointId, String csmsUrl,
                          ClientCoreProfile core, ClientRemoteTriggerProfile remoteTrigger) {
        this.chargePointId = chargePointId;
        this.csmsUrl = csmsUrl;
        this.core = core;
        this.remoteTrigger = remoteTrigger;
    }

    @Override
    public boolean connect() {
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                jsonClient = new JSONClient(core, chargePointId);
                jsonClient.addFeatureProfile(remoteTrigger);
                jsonClient.connect(csmsUrl, null);
                connected.set(true);
                log.info("[{}] Connected to CSMS at {}", chargePointId, csmsUrl);
                return true;
            } catch (Exception e) {
                log.warn("[{}] Connection attempt {} failed: {}", chargePointId, attempt, e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }
        log.error("[{}] Could not connect to CSMS after {} attempts", chargePointId, MAX_ATTEMPTS);
        return false;
    }

    @Override
    public void disconnect() {
        if (jsonClient != null) {
            try {
                jsonClient.disconnect();
            } catch (Exception e) {
                log.warn("[{}] Error during disconnect: {}", chargePointId, e.getMessage());
            }
        }
        connected.set(false);
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public CompletableFuture<?> send(Request request) {
        if (jsonClient == null || !connected.get()) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Not connected"));
            return failed;
        }
        try {
            return jsonClient.send(request).toCompletableFuture();
        } catch (Exception e) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
}
