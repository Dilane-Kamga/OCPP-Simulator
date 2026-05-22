package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.model.Request;

import java.util.concurrent.CompletableFuture;

/**
 * Transport-agnostic abstraction over the OCPP-J client used by {@link ChargePointSimulator}.
 *
 * <p>Production code wires this to a {@link JsonOcppClient} backed by {@code JSONClient}; tests
 * inject a mock so state-machine transitions can be asserted without a real WebSocket.
 */
public interface OcppClient {

    /**
     * Open the connection to the CSMS. Implementations may apply retry/backoff internally.
     *
     * @return {@code true} once the WebSocket session is established, {@code false} if all
     *         attempts were exhausted.
     */
    boolean connect();

    /** Close the connection. Safe to call when not connected. */
    void disconnect();

    /** @return {@code true} if {@link #connect()} succeeded and {@link #disconnect()} has not been called. */
    boolean isConnected();

    /**
     * Send an outbound OCPP request.
     *
     * @param request the request payload (e.g. {@code BootNotificationRequest}, {@code MeterValuesRequest})
     * @return a future that completes with the {@code Confirmation}, or completes exceptionally
     *         if the client is not connected or the SDK rejects the call.
     */
    CompletableFuture<?> send(Request request);
}
