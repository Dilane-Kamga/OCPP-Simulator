package com.accenture.nexcharge.simulator.ocpp;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import com.accenture.nexcharge.simulator.repository.MeterReadingRepository;
import com.accenture.nexcharge.simulator.service.AuthorizationService;
import com.accenture.nexcharge.simulator.service.LiveEventService;
import com.accenture.nexcharge.simulator.service.LogService;
import eu.chargetime.ocpp.model.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsmsEventHandlerTest {

    @Mock ChargePointRepository chargePointRepository;
    @Mock ConnectorRepository connectorRepository;
    @Mock ChargingSessionRepository sessionRepository;
    @Mock MeterReadingRepository meterRepository;
    @Mock LogService logService;
    @Mock LiveEventService liveEventService;
    @Mock OcppSessionRegistry registry;
    @Mock AuthorizationService authorizationService;

    SimulatorProperties properties;
    CsmsEventHandler handler;

    @BeforeEach
    void setUp() {
        properties = new SimulatorProperties(true, 15, 30, 10, 0.05, 0.02, List.of(), List.of("RFID-001"));
        handler = new CsmsEventHandler(
                chargePointRepository, connectorRepository, sessionRepository,
                meterRepository, logService, liveEventService, registry, properties,
                authorizationService);
    }

    @Test
    void bootNotificationAcceptsAndSavesEntity() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        when(chargePointRepository.findById("BORNE_A")).thenReturn(Optional.empty());

        BootNotificationRequest req = new BootNotificationRequest("Legrand", "Green'Up Premium");
        req.setChargePointSerialNumber("LGR-001");
        req.setFirmwareVersion("1.4.2");

        BootNotificationConfirmation conf = handler.handleBootNotificationRequest(sessionId, req);

        assertThat(conf.getStatus()).isEqualTo(RegistrationStatus.Accepted);
        assertThat(conf.getInterval()).isEqualTo(30);
        verify(chargePointRepository).save(any(ChargePointEntity.class));
    }

    @Test
    void heartbeatUpdatesLastHeartbeatTimestamp() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        ChargePointEntity cp = ChargePointEntity.builder().chargePointId("BORNE_A").build();
        when(chargePointRepository.findById("BORNE_A")).thenReturn(Optional.of(cp));

        HeartbeatConfirmation conf = handler.handleHeartbeatRequest(sessionId, new HeartbeatRequest());

        assertThat(conf.getCurrentTime()).isNotNull();
        verify(chargePointRepository).save(cp);
        assertThat(cp.getLastHeartbeat()).isNotNull();
    }

    @Test
    void authorizeSeededTagAccepted() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        when(authorizationService.authorize(eq("RFID-001"), any())).thenReturn(AuthorizationStatus.Accepted);
        AuthorizeRequest req = new AuthorizeRequest("RFID-001");

        AuthorizeConfirmation conf = handler.handleAuthorizeRequest(sessionId, req);

        IdTagInfo info = conf.getIdTagInfo();
        assertThat(info.getStatus()).isEqualTo(AuthorizationStatus.Accepted);
    }

    @Test
    void startTransactionAssignsIncrementingTransactionIds() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(ConnectorEntity.builder().chargePointId("BORNE_A").connectorId(1).build()));

        StartTransactionRequest req1 = new StartTransactionRequest(1, "RFID-001", 0, ZonedDateTime.now());
        StartTransactionRequest req2 = new StartTransactionRequest(1, "RFID-001", 0, ZonedDateTime.now());

        StartTransactionConfirmation c1 = handler.handleStartTransactionRequest(sessionId, req1);
        StartTransactionConfirmation c2 = handler.handleStartTransactionRequest(sessionId, req2);

        assertThat(c1.getTransactionId()).isGreaterThanOrEqualTo(1000);
        assertThat(c2.getTransactionId()).isEqualTo(c1.getTransactionId() + 1);
        assertThat(c1.getIdTagInfo().getStatus()).isEqualTo(AuthorizationStatus.Accepted);
        verify(sessionRepository, org.mockito.Mockito.atLeast(2)).save(any(ChargingSessionEntity.class));
    }

    @Test
    void stopTransactionMarksSessionCompleted() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(1L).transactionId(1001).chargePointId("BORNE_A").connectorId(1)
                .meterStartWh(0.0).startTime(java.time.Instant.now())
                .status(SessionStatus.Active).build();
        when(sessionRepository.findByTransactionId(1001)).thenReturn(Optional.of(entity));

        StopTransactionRequest req = new StopTransactionRequest(5000, ZonedDateTime.now(), 1001);
        req.setReason(Reason.Local);

        StopTransactionConfirmation conf = handler.handleStopTransactionRequest(sessionId, req);

        assertThat(conf).isNotNull();
        ArgumentCaptor<ChargingSessionEntity> captor = ArgumentCaptor.forClass(ChargingSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        ChargingSessionEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.Completed);
        assertThat(saved.getEnergyDeliveredKwh()).isEqualTo(5.0);
        assertThat(saved.getStopReason()).isEqualTo("Local");
    }

    @Test
    void statusNotificationUpdatesConnector() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        ConnectorEntity connector = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1).build();
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(connector));

        StatusNotificationRequest req = new StatusNotificationRequest(
                1, ChargePointErrorCode.NoError, ChargePointStatus.Charging);

        StatusNotificationConfirmation conf = handler.handleStatusNotificationRequest(sessionId, req);

        assertThat(conf).isNotNull();
        verify(connectorRepository).save(connector);
        assertThat(connector.getStatus().toString()).isEqualTo("Charging");
    }

    @Test
    void meterValuesPersistsSampledValues() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));

        SampledValue sv = new SampledValue();
        sv.setValue("7200");
        sv.setMeasurand("Power.Active.Import");
        MeterValue mv = new MeterValue();
        mv.setTimestamp(ZonedDateTime.now());
        mv.setSampledValue(new SampledValue[]{sv});

        MeterValuesRequest req = new MeterValuesRequest(1);
        req.setTransactionId(1001);
        req.setMeterValue(new MeterValue[]{mv});

        MeterValuesConfirmation conf = handler.handleMeterValuesRequest(sessionId, req);

        assertThat(conf).isNotNull();
        verify(meterRepository).saveAll(any());
    }

    @Test
    void dataTransferAlwaysAccepts() {
        UUID sessionId = UUID.randomUUID();
        DataTransferRequest req = new DataTransferRequest("Legrand");

        DataTransferConfirmation conf = handler.handleDataTransferRequest(sessionId, req);

        assertThat(conf.getStatus()).isEqualTo(DataTransferStatus.Accepted);
    }

    // ---- Maintenance-block gate tests ----

    @Test
    void statusNotification_blockedConnector_nonFaulted_doesNotOverwriteStatus() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        // Connector is blocked, currently shows Available (operator-controlled state)
        ConnectorEntity connector = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Available)
                .blocked(true).blockedReason("Maintenance").build();
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(connector));

        // Borne reports Charging — should be suppressed while blocked
        StatusNotificationRequest req = new StatusNotificationRequest(
                1, ChargePointErrorCode.NoError, ChargePointStatus.Charging);
        handler.handleStatusNotificationRequest(sessionId, req);

        ArgumentCaptor<ConnectorEntity> captor = ArgumentCaptor.forClass(ConnectorEntity.class);
        verify(connectorRepository).save(captor.capture());
        // Status must NOT have changed to Charging
        assertThat(captor.getValue().getStatus()).isEqualTo(ConnectorStatus.Available);
    }

    @Test
    void statusNotification_blockedConnector_faultedStatus_isAllowedThrough() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        // Connector is blocked but borne reports a physical fault
        ConnectorEntity connector = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Available)
                .blocked(true).blockedReason("Maintenance").build();
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(connector));

        // Physical safety fault must override the admin block
        StatusNotificationRequest req = new StatusNotificationRequest(
                1, ChargePointErrorCode.GroundFailure, ChargePointStatus.Faulted);
        handler.handleStatusNotificationRequest(sessionId, req);

        ArgumentCaptor<ConnectorEntity> captor = ArgumentCaptor.forClass(ConnectorEntity.class);
        verify(connectorRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ConnectorStatus.Faulted);
    }

    @Test
    void statusNotification_unblockedConnector_updatesStatusNormally() {
        UUID sessionId = UUID.randomUUID();
        when(registry.findChargePointId(sessionId)).thenReturn(Optional.of("BORNE_A"));
        ConnectorEntity connector = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Available)
                .blocked(false).build();
        when(connectorRepository.findByChargePointIdAndConnectorId("BORNE_A", 1))
                .thenReturn(Optional.of(connector));

        StatusNotificationRequest req = new StatusNotificationRequest(
                1, ChargePointErrorCode.NoError, ChargePointStatus.Charging);
        handler.handleStatusNotificationRequest(sessionId, req);

        ArgumentCaptor<ConnectorEntity> captor = ArgumentCaptor.forClass(ConnectorEntity.class);
        verify(connectorRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ConnectorStatus.Charging);
    }
}
