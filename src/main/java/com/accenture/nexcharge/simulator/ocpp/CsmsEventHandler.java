package com.accenture.nexcharge.simulator.ocpp;

import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.dto.LiveEventDto;
import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.model.enums.LiveEventType;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import com.accenture.nexcharge.simulator.repository.MeterReadingRepository;
import com.accenture.nexcharge.simulator.service.LiveEventService;
import com.accenture.nexcharge.simulator.service.LogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler;
import eu.chargetime.ocpp.model.core.AuthorizationStatus;
import eu.chargetime.ocpp.model.core.AuthorizeConfirmation;
import eu.chargetime.ocpp.model.core.AuthorizeRequest;
import eu.chargetime.ocpp.model.core.BootNotificationConfirmation;
import eu.chargetime.ocpp.model.core.BootNotificationRequest;
import eu.chargetime.ocpp.model.core.ChargePointStatus;
import eu.chargetime.ocpp.model.core.DataTransferConfirmation;
import eu.chargetime.ocpp.model.core.DataTransferRequest;
import eu.chargetime.ocpp.model.core.DataTransferStatus;
import eu.chargetime.ocpp.model.core.HeartbeatConfirmation;
import eu.chargetime.ocpp.model.core.HeartbeatRequest;
import eu.chargetime.ocpp.model.core.IdTagInfo;
import eu.chargetime.ocpp.model.core.MeterValue;
import eu.chargetime.ocpp.model.core.MeterValuesConfirmation;
import eu.chargetime.ocpp.model.core.MeterValuesRequest;
import eu.chargetime.ocpp.model.core.RegistrationStatus;
import eu.chargetime.ocpp.model.core.SampledValue;
import eu.chargetime.ocpp.model.core.StartTransactionConfirmation;
import eu.chargetime.ocpp.model.core.StartTransactionRequest;
import eu.chargetime.ocpp.model.core.StatusNotificationConfirmation;
import eu.chargetime.ocpp.model.core.StatusNotificationRequest;
import eu.chargetime.ocpp.model.core.StopTransactionConfirmation;
import eu.chargetime.ocpp.model.core.StopTransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsmsEventHandler implements ServerCoreEventHandler {

    private static final int TRANSACTION_ID_START = 1000;

    private final ChargePointRepository chargePointRepository;
    private final ConnectorRepository connectorRepository;
    private final ChargingSessionRepository sessionRepository;
    private final MeterReadingRepository meterRepository;
    private final LogService logService;
    private final LiveEventService liveEventService;
    private final OcppSessionRegistry registry;
    private final SimulatorProperties properties;

    private final AtomicInteger transactionCounter = new AtomicInteger(TRANSACTION_ID_START);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    @Transactional
    public BootNotificationConfirmation handleBootNotificationRequest(UUID sessionIndex, BootNotificationRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        log.info("[CSMS] BootNotification from {}: {} {}",
                chargePointId, request.getChargePointVendor(), request.getChargePointModel());
        logIncoming(chargePointId, "BootNotification", request);

        ChargePointEntity entity = chargePointRepository.findById(chargePointId)
                .orElse(ChargePointEntity.builder()
                        .chargePointId(chargePointId)
                        .registeredAt(Instant.now())
                        .build());

        entity.setVendor(request.getChargePointVendor());
        entity.setModel(request.getChargePointModel());
        entity.setSerialNumber(request.getChargePointSerialNumber());
        entity.setFirmwareVersion(request.getFirmwareVersion());
        entity.setStatus(com.accenture.nexcharge.simulator.model.enums.ChargePointStatus.Available);
        entity.setOnline(true);
        entity.setLastHeartbeat(Instant.now());
        entity.setErrorCode("NoError");
        chargePointRepository.save(entity);

        liveEventService.publish(LiveEventDto.of(LiveEventType.CHARGE_POINT_CONNECTED, chargePointId,
                Map.of("vendor", safe(request.getChargePointVendor()),
                        "model", safe(request.getChargePointModel()))));

        return new BootNotificationConfirmation(
                ZonedDateTime.now(), properties.heartbeatIntervalSeconds(), RegistrationStatus.Accepted);
    }

    @Override
    @Transactional
    public HeartbeatConfirmation handleHeartbeatRequest(UUID sessionIndex, HeartbeatRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        logIncoming(chargePointId, "Heartbeat", request);

        chargePointRepository.findById(chargePointId).ifPresent(cp -> {
            cp.setLastHeartbeat(Instant.now());
            cp.setOnline(true);
            chargePointRepository.save(cp);
        });

        liveEventService.publish(LiveEventDto.of(LiveEventType.HEARTBEAT, chargePointId, Map.of()));
        return new HeartbeatConfirmation(ZonedDateTime.now());
    }

    @Override
    public AuthorizeConfirmation handleAuthorizeRequest(UUID sessionIndex, AuthorizeRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        logIncoming(chargePointId, "Authorize", request);

        IdTagInfo info = new IdTagInfo(AuthorizationStatus.Accepted);
        info.setExpiryDate(ZonedDateTime.now().plusYears(1));
        return new AuthorizeConfirmation(info);
    }

    @Override
    @Transactional
    public StartTransactionConfirmation handleStartTransactionRequest(UUID sessionIndex, StartTransactionRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        log.info("[CSMS] StartTransaction from {} connector {} idTag {}",
                chargePointId, request.getConnectorId(), request.getIdTag());
        logIncoming(chargePointId, "StartTransaction", request);

        int transactionId = transactionCounter.getAndIncrement();
        Instant startTime = request.getTimestamp() != null
                ? request.getTimestamp().toInstant()
                : Instant.now();

        ChargingSessionEntity session = ChargingSessionEntity.builder()
                .transactionId(transactionId)
                .chargePointId(chargePointId)
                .connectorId(request.getConnectorId())
                .idTag(request.getIdTag())
                .startTime(startTime)
                .meterStartWh(request.getMeterStart() == null ? 0.0 : request.getMeterStart().doubleValue())
                .status(SessionStatus.Active)
                .build();
        sessionRepository.save(session);

        connectorRepository.findByChargePointIdAndConnectorId(chargePointId, request.getConnectorId())
                .ifPresent(c -> {
                    c.setStatus(ConnectorStatus.Charging);
                    connectorRepository.save(c);
                });

        liveEventService.publish(LiveEventDto.of(LiveEventType.SESSION_STARTED, chargePointId, request.getConnectorId(),
                Map.of("transactionId", transactionId, "idTag", safe(request.getIdTag()))));

        IdTagInfo info = new IdTagInfo(AuthorizationStatus.Accepted);
        return new StartTransactionConfirmation(info, transactionId);
    }

    @Override
    @Transactional
    public StopTransactionConfirmation handleStopTransactionRequest(UUID sessionIndex, StopTransactionRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        log.info("[CSMS] StopTransaction from {} txn {} reason {}",
                chargePointId, request.getTransactionId(), request.getReason());
        logIncoming(chargePointId, "StopTransaction", request);

        sessionRepository.findByTransactionId(request.getTransactionId()).ifPresent(s -> {
            double meterStop = request.getMeterStop() == null ? 0.0 : request.getMeterStop().doubleValue();
            double meterStart = s.getMeterStartWh() == null ? 0.0 : s.getMeterStartWh();
            s.setMeterStopWh(meterStop);
            s.setEnergyDeliveredKwh((meterStop - meterStart) / 1000.0);
            s.setStopTime(request.getTimestamp() != null ? request.getTimestamp().toInstant() : Instant.now());
            s.setStopReason(request.getReason() != null ? request.getReason().name() : "Local");
            s.setStatus(SessionStatus.Completed);
            sessionRepository.save(s);

            connectorRepository.findByChargePointIdAndConnectorId(chargePointId, s.getConnectorId())
                    .ifPresent(c -> {
                        c.setStatus(ConnectorStatus.Available);
                        c.setCurrentPowerKw(0.0);
                        c.setCurrentAmps(0.0);
                        connectorRepository.save(c);
                    });

            liveEventService.publish(LiveEventDto.of(LiveEventType.SESSION_STOPPED, chargePointId, s.getConnectorId(),
                    Map.of("transactionId", s.getTransactionId(),
                            "energyKwh", s.getEnergyDeliveredKwh(),
                            "reason", s.getStopReason())));
        });

        IdTagInfo info = new IdTagInfo(AuthorizationStatus.Accepted);
        StopTransactionConfirmation conf = new StopTransactionConfirmation();
        conf.setIdTagInfo(info);
        return conf;
    }

    @Override
    @Transactional
    public StatusNotificationConfirmation handleStatusNotificationRequest(UUID sessionIndex, StatusNotificationRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        log.info("[CSMS] StatusNotification {} connector {} status {} error {}",
                chargePointId, request.getConnectorId(), request.getStatus(), request.getErrorCode());
        logIncoming(chargePointId, "StatusNotification", request);

        ChargePointStatus rawStatus = request.getStatus();
        String statusName = rawStatus != null ? rawStatus.name() : "Available";
        ConnectorStatus newStatus = ConnectorStatus.valueOf(statusName);
        String errorCode = request.getErrorCode() != null ? request.getErrorCode().name() : "NoError";

        if (request.getConnectorId() != null && request.getConnectorId() == 0) {
            chargePointRepository.findById(chargePointId).ifPresent(cp -> {
                cp.setStatus(com.accenture.nexcharge.simulator.model.enums.ChargePointStatus.valueOf(statusName));
                cp.setErrorCode(errorCode);
                chargePointRepository.save(cp);
            });
        } else {
            ConnectorEntity connector = connectorRepository
                    .findByChargePointIdAndConnectorId(chargePointId, request.getConnectorId())
                    .orElseGet(() -> ConnectorEntity.builder()
                            .chargePointId(chargePointId)
                            .connectorId(request.getConnectorId())
                            .build());
            connector.setStatus(newStatus);
            connector.setErrorCode(errorCode);
            connectorRepository.save(connector);

            if (newStatus == ConnectorStatus.Faulted) {
                liveEventService.publish(LiveEventDto.of(LiveEventType.FAULT, chargePointId,
                        request.getConnectorId(), Map.of("errorCode", errorCode)));
            }
        }

        liveEventService.publish(LiveEventDto.of(LiveEventType.STATUS_CHANGE, chargePointId,
                request.getConnectorId(),
                Map.of("status", statusName, "errorCode", errorCode)));

        return new StatusNotificationConfirmation();
    }

    @Override
    @Transactional
    public MeterValuesConfirmation handleMeterValuesRequest(UUID sessionIndex, MeterValuesRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        logIncoming(chargePointId, "MeterValues", request);

        Integer connectorId = request.getConnectorId();
        Integer txId = request.getTransactionId();
        List<MeterReadingEntity> readings = new ArrayList<>();
        Map<String, Double> latest = new HashMap<>();

        if (request.getMeterValue() != null) {
            for (MeterValue mv : request.getMeterValue()) {
                Instant ts = mv.getTimestamp() != null ? mv.getTimestamp().toInstant() : Instant.now();
                if (mv.getSampledValue() == null) continue;
                for (SampledValue sv : mv.getSampledValue()) {
                    double parsed = parseDouble(sv.getValue());
                    String measurand = sv.getMeasurand() != null ? sv.getMeasurand() : "Energy.Active.Import.Register";
                    String unit = sv.getUnit() != null ? sv.getUnit() : "Wh";
                    readings.add(MeterReadingEntity.builder()
                            .chargePointId(chargePointId)
                            .connectorId(connectorId)
                            .transactionId(txId)
                            .measurand(measurand)
                            .value(parsed)
                            .unit(unit)
                            .timestamp(ts)
                            .build());
                    latest.put(measurand, parsed);
                }
            }
        }

        if (!readings.isEmpty()) {
            meterRepository.saveAll(readings);

            connectorRepository.findByChargePointIdAndConnectorId(chargePointId, connectorId)
                    .ifPresent(c -> {
                        if (latest.containsKey("Power.Active.Import"))
                            c.setCurrentPowerKw(latest.get("Power.Active.Import") / 1000.0);
                        if (latest.containsKey("Current.Import"))
                            c.setCurrentAmps(latest.get("Current.Import"));
                        if (latest.containsKey("Voltage"))
                            c.setVoltage(latest.get("Voltage"));
                        if (latest.containsKey("Temperature"))
                            c.setTemperatureCelsius(latest.get("Temperature"));
                        if (latest.containsKey("Energy.Active.Import.Register"))
                            c.setTotalEnergyKwh(latest.get("Energy.Active.Import.Register") / 1000.0);
                        connectorRepository.save(c);
                    });

            Map<String, Object> payload = new HashMap<>();
            payload.put("readings", latest);
            payload.put("transactionId", txId);
            liveEventService.publish(LiveEventDto.of(LiveEventType.METER_UPDATE, chargePointId, connectorId, payload));
        }

        return new MeterValuesConfirmation();
    }

    @Override
    public DataTransferConfirmation handleDataTransferRequest(UUID sessionIndex, DataTransferRequest request) {
        String chargePointId = registry.findChargePointId(sessionIndex).orElse("UNKNOWN");
        logIncoming(chargePointId, "DataTransfer", request);
        return new DataTransferConfirmation(DataTransferStatus.Accepted);
    }

    private void logIncoming(String chargePointId, String action, Object request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            logService.log(chargePointId, LogDirection.IN, action, payload);
        } catch (Exception e) {
            logService.log(chargePointId, LogDirection.IN, action, "<unserializable: " + e.getMessage() + ">");
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
