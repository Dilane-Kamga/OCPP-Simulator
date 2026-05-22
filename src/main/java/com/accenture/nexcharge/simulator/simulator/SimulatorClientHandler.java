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

    private final String chargePointId;
    private final InboundCommands commands;

    public SimulatorClientHandler(String chargePointId, InboundCommands commands) {
        this.chargePointId = chargePointId;
        this.commands = commands;
    }

    @Override
    public ChangeAvailabilityConfirmation handleChangeAvailabilityRequest(ChangeAvailabilityRequest request) {
        return new ChangeAvailabilityConfirmation(AvailabilityStatus.Accepted);
    }

    @Override
    public GetConfigurationConfirmation handleGetConfigurationRequest(GetConfigurationRequest request) {
        return new GetConfigurationConfirmation();
    }

    @Override
    public ChangeConfigurationConfirmation handleChangeConfigurationRequest(ChangeConfigurationRequest request) {
        return new ChangeConfigurationConfirmation(ConfigurationStatus.Accepted);
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
