package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.feature.profile.ClientCoreEventHandler;
import eu.chargetime.ocpp.feature.profile.ClientRemoteTriggerEventHandler;
import eu.chargetime.ocpp.model.core.AvailabilityStatus;
import eu.chargetime.ocpp.model.core.ChangeAvailabilityConfirmation;
import eu.chargetime.ocpp.model.core.ChangeAvailabilityRequest;
import eu.chargetime.ocpp.model.core.ChangeConfigurationConfirmation;
import eu.chargetime.ocpp.model.core.ChangeConfigurationRequest;
import eu.chargetime.ocpp.model.core.ClearCacheConfirmation;
import eu.chargetime.ocpp.model.core.ClearCacheRequest;
import eu.chargetime.ocpp.model.core.ClearCacheStatus;
import eu.chargetime.ocpp.model.core.ConfigurationStatus;
import eu.chargetime.ocpp.model.core.DataTransferConfirmation;
import eu.chargetime.ocpp.model.core.DataTransferRequest;
import eu.chargetime.ocpp.model.core.DataTransferStatus;
import eu.chargetime.ocpp.model.core.GetConfigurationConfirmation;
import eu.chargetime.ocpp.model.core.GetConfigurationRequest;
import eu.chargetime.ocpp.model.core.RemoteStartStopStatus;
import eu.chargetime.ocpp.model.core.RemoteStartTransactionConfirmation;
import eu.chargetime.ocpp.model.core.RemoteStartTransactionRequest;
import eu.chargetime.ocpp.model.core.RemoteStopTransactionConfirmation;
import eu.chargetime.ocpp.model.core.RemoteStopTransactionRequest;
import eu.chargetime.ocpp.model.core.ResetConfirmation;
import eu.chargetime.ocpp.model.core.ResetRequest;
import eu.chargetime.ocpp.model.core.ResetStatus;
import eu.chargetime.ocpp.model.core.ResetType;
import eu.chargetime.ocpp.model.core.UnlockConnectorConfirmation;
import eu.chargetime.ocpp.model.core.UnlockConnectorRequest;
import eu.chargetime.ocpp.model.core.UnlockStatus;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageConfirmation;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * Inbound OCPP message handler for a single simulated charge point.
 * Implements both the Core profile (CSMS-initiated commands like RemoteStart/Stop, Reset, Unlock)
 * and the Remote Trigger profile (TriggerMessage) on the client side.
 *
 * <p>All routing decisions delegate to the {@link InboundCommands} callback so the simulator
 * state machine can react without coupling to the SDK message types.
 */
@Slf4j
public class SimulatorClientHandler implements ClientCoreEventHandler, ClientRemoteTriggerEventHandler {

    /** Default connector when the CSMS omits one in a RemoteStartTransaction. */
    private static final int DEFAULT_CONNECTOR_ID = 1;
    /** Connector identifier 0 represents the charge point itself (used for global StatusNotification). */
    private static final int MAIN_CONTROLLER_CONNECTOR_ID = 0;

    public interface InboundCommands {
        void onRemoteStart(int connectorId, String idTag);

        void onRemoteStop(int transactionId);

        void onReset(boolean hard);

        void onUnlock(int connectorId);

        void onTriggerStatusNotification(int connectorId);

        void onTriggerHeartbeat();

        void onTriggerMeterValues(int connectorId);

        void onTriggerBootNotification();
    }

    /**
     * Callback interface that gives the handler read/write access to the charge point's
     * in-memory configuration map.  Keeps the handler decoupled from
     * {@link ChargePointSimulator}.
     */
    public interface ConfigurationStore {
        /**
         * Attempt to set a configuration key to a new value.
         *
         * @param key   the configuration key
         * @param value the new value string
         * @return {@code true} when the key is known and was updated;
         *         {@code false} when the key is unknown (→ CP replies Rejected)
         */
        boolean set(String key, String value);

        /**
         * Return all known keys, or only the requested subset when {@code requestedKeys}
         * is non-null and non-empty.
         *
         * @param requestedKeys keys to filter by, or {@code null}/empty for all
         * @return list of matching entries
         */
        java.util.List<eu.chargetime.ocpp.model.core.KeyValueType> get(
                java.util.List<String> requestedKeys);
    }

    private final String chargePointId;
    private final InboundCommands commands;
    private final ConfigurationStore configStore;

    public SimulatorClientHandler(String chargePointId, InboundCommands commands,
                                  ConfigurationStore configStore) {
        this.chargePointId = chargePointId;
        this.commands = commands;
        this.configStore = configStore;
    }

    @Override
    public ChangeAvailabilityConfirmation handleChangeAvailabilityRequest(ChangeAvailabilityRequest request) {
        return new ChangeAvailabilityConfirmation(AvailabilityStatus.Accepted);
    }

    @Override
    public GetConfigurationConfirmation handleGetConfigurationRequest(GetConfigurationRequest request) {
        String[] requestedKeys = request.getKey();
        java.util.List<String> filter = (requestedKeys == null || requestedKeys.length == 0)
                ? null
                : java.util.Arrays.asList(requestedKeys);

        java.util.List<eu.chargetime.ocpp.model.core.KeyValueType> known = configStore.get(filter);

        GetConfigurationConfirmation confirmation = new GetConfigurationConfirmation();
        confirmation.setConfigurationKey(
                known.toArray(new eu.chargetime.ocpp.model.core.KeyValueType[0]));

        if (filter != null) {
            // Collect keys that were requested but not found in the store
            java.util.Set<String> knownKeyNames = known.stream()
                    .map(eu.chargetime.ocpp.model.core.KeyValueType::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            java.util.List<String> unknown = filter.stream()
                    .filter(k -> !knownKeyNames.contains(k))
                    .toList();
            if (!unknown.isEmpty()) {
                confirmation.setUnknownKey(unknown.toArray(String[]::new));
            }
        }

        log.info("[{}] GetConfiguration: {} known, {} unknown",
                chargePointId, known.size(),
                confirmation.getUnknownKey() == null ? 0 : confirmation.getUnknownKey().length);
        return confirmation;
    }

    @Override
    public ChangeConfigurationConfirmation handleChangeConfigurationRequest(ChangeConfigurationRequest request) {
        String key = request.getKey();
        String value = request.getValue();
        log.info("[{}] ChangeConfiguration key={} value={}", chargePointId, key, value);

        if (key == null || value == null) {
            return new ChangeConfigurationConfirmation(ConfigurationStatus.Rejected);
        }

        boolean applied = configStore.set(key, value);
        ConfigurationStatus status = applied ? ConfigurationStatus.Accepted : ConfigurationStatus.Rejected;
        log.info("[{}] ChangeConfiguration key={} → {}", chargePointId, key, status);
        return new ChangeConfigurationConfirmation(status);
    }

    @Override
    public ClearCacheConfirmation handleClearCacheRequest(ClearCacheRequest request) {
        return new ClearCacheConfirmation(ClearCacheStatus.Accepted);
    }

    @Override
    public DataTransferConfirmation handleDataTransferRequest(DataTransferRequest request) {
        return new DataTransferConfirmation(DataTransferStatus.Accepted);
    }

    @Override
    public RemoteStartTransactionConfirmation handleRemoteStartTransactionRequest(RemoteStartTransactionRequest request) {
        log.info("[{}] RemoteStart received connector={} idTag={}",
                chargePointId, request.getConnectorId(), request.getIdTag());
        int connectorId = request.getConnectorId() == null ? DEFAULT_CONNECTOR_ID : request.getConnectorId();
        commands.onRemoteStart(connectorId, request.getIdTag());
        return new RemoteStartTransactionConfirmation(RemoteStartStopStatus.Accepted);
    }

    @Override
    public RemoteStopTransactionConfirmation handleRemoteStopTransactionRequest(RemoteStopTransactionRequest request) {
        log.info("[{}] RemoteStop received txn={}", chargePointId, request.getTransactionId());
        if (request.getTransactionId() != null) {
            commands.onRemoteStop(request.getTransactionId());
        }
        return new RemoteStopTransactionConfirmation(RemoteStartStopStatus.Accepted);
    }

    @Override
    public ResetConfirmation handleResetRequest(ResetRequest request) {
        log.info("[{}] Reset received type={}", chargePointId, request.getType());
        commands.onReset(request.getType() == ResetType.Hard);
        return new ResetConfirmation(ResetStatus.Accepted);
    }

    @Override
    public UnlockConnectorConfirmation handleUnlockConnectorRequest(UnlockConnectorRequest request) {
        log.info("[{}] UnlockConnector received connector={}", chargePointId, request.getConnectorId());
        if (request.getConnectorId() != null) {
            commands.onUnlock(request.getConnectorId());
        }
        return new UnlockConnectorConfirmation(UnlockStatus.Unlocked);
    }

    @Override
    public TriggerMessageConfirmation handleTriggerMessageRequest(TriggerMessageRequest request) {
        TriggerMessageRequestType type = request.getRequestedMessage();
        Integer connectorId = request.getConnectorId();
        log.info("[{}] TriggerMessage received type={} connector={}", chargePointId, type, connectorId);
        if (type == null) {
            return new TriggerMessageConfirmation(TriggerMessageStatus.Rejected);
        }
        switch (type) {
            case StatusNotification ->
                    commands.onTriggerStatusNotification(connectorId == null ? MAIN_CONTROLLER_CONNECTOR_ID : connectorId);
            case Heartbeat -> commands.onTriggerHeartbeat();
            case MeterValues ->
                    commands.onTriggerMeterValues(connectorId == null ? DEFAULT_CONNECTOR_ID : connectorId);
            case BootNotification -> commands.onTriggerBootNotification();
            default -> {
                return new TriggerMessageConfirmation(TriggerMessageStatus.NotImplemented);
            }
        }
        return new TriggerMessageConfirmation(TriggerMessageStatus.Accepted);
    }
}
