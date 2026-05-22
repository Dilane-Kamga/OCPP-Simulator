package com.accenture.nexcharge.simulator.simulator;

import eu.chargetime.ocpp.model.core.RemoteStartStopStatus;
import eu.chargetime.ocpp.model.core.RemoteStartTransactionConfirmation;
import eu.chargetime.ocpp.model.core.RemoteStartTransactionRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageConfirmation;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SimulatorClientHandlerTest {

    private final SimulatorClientHandler.InboundCommands commands =
            mock(SimulatorClientHandler.InboundCommands.class);
    private final SimulatorClientHandler.ConfigurationStore configStore =
            mock(SimulatorClientHandler.ConfigurationStore.class);
    private final SimulatorClientHandler handler =
            new SimulatorClientHandler("BORNE_A", commands, configStore);

    @Test
    void triggerHeartbeatRoutesToInbound() {
        TriggerMessageRequest req = new TriggerMessageRequest(TriggerMessageRequestType.Heartbeat);

        TriggerMessageConfirmation conf = handler.handleTriggerMessageRequest(req);

        assertThat(conf.getStatus()).isEqualTo(TriggerMessageStatus.Accepted);
        verify(commands).onTriggerHeartbeat();
    }

    @Test
    void triggerStatusNotificationRoutesToInbound() {
        TriggerMessageRequest req = new TriggerMessageRequest(TriggerMessageRequestType.StatusNotification);
        req.setConnectorId(1);

        TriggerMessageConfirmation conf = handler.handleTriggerMessageRequest(req);

        assertThat(conf.getStatus()).isEqualTo(TriggerMessageStatus.Accepted);
        verify(commands).onTriggerStatusNotification(1);
    }

    @Test
    void remoteStartRoutesToInbound() {
        RemoteStartTransactionRequest req = new RemoteStartTransactionRequest("RFID-0001");
        req.setConnectorId(1);

        RemoteStartTransactionConfirmation conf = handler.handleRemoteStartTransactionRequest(req);

        assertThat(conf.getStatus()).isEqualTo(RemoteStartStopStatus.Accepted);
        verify(commands).onRemoteStart(1, "RFID-0001");
    }

    @Test
    void remoteStartWithNullConnectorDefaultsToOne() {
        RemoteStartTransactionRequest req = new RemoteStartTransactionRequest("RFID-0002");
        // connectorId left null

        RemoteStartTransactionConfirmation conf = handler.handleRemoteStartTransactionRequest(req);

        assertThat(conf.getStatus()).isEqualTo(RemoteStartStopStatus.Accepted);
        verify(commands).onRemoteStart(1, "RFID-0002");
    }
}
