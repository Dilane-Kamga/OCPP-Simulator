package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.ChargePointDto;
import com.accenture.nexcharge.simulator.model.dto.ConnectorDto;
import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChargePointService {

    private final ChargePointRepository chargePointRepository;
    private final ConnectorRepository connectorRepository;

    public List<ChargePointDto> getAll() {
        return chargePointRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public ChargePointDto getById(String chargePointId) {
        ChargePointEntity entity = chargePointRepository.findById(chargePointId)
                .orElseThrow(() -> new ChargePointNotFoundException(chargePointId));
        return toDto(entity);
    }

    public List<ConnectorDto> getConnectors(String chargePointId) {
        if (!chargePointRepository.existsById(chargePointId)) {
            throw new ChargePointNotFoundException(chargePointId);
        }
        return connectorRepository.findByChargePointIdOrderByConnectorIdAsc(chargePointId).stream()
                .map(this::toConnectorDto)
                .toList();
    }

    @Transactional
    public ConnectorDto blockConnector(String chargePointId, int connectorId, String reason) {
        ConnectorEntity connector = connectorRepository
                .findByChargePointIdAndConnectorId(chargePointId, connectorId)
                .orElseThrow(() -> new ConnectorNotFoundException(chargePointId, connectorId));
        connector.setBlocked(true);
        connector.setBlockedReason(reason);
        connector.setBlockedAt(Instant.now());
        connectorRepository.save(connector);
        return toConnectorDto(connector);
    }

    @Transactional
    public ConnectorDto unblockConnector(String chargePointId, int connectorId) {
        ConnectorEntity connector = connectorRepository
                .findByChargePointIdAndConnectorId(chargePointId, connectorId)
                .orElseThrow(() -> new ConnectorNotFoundException(chargePointId, connectorId));
        connector.setBlocked(false);
        connector.setBlockedReason(null);
        connector.setBlockedAt(null);
        connectorRepository.save(connector);
        return toConnectorDto(connector);
    }

    private ChargePointDto toDto(ChargePointEntity cp) {
        List<ConnectorDto> connectors = connectorRepository
                .findByChargePointIdOrderByConnectorIdAsc(cp.getChargePointId()).stream()
                .map(this::toConnectorDto)
                .toList();

        return new ChargePointDto(
                cp.getChargePointId(),
                cp.getSite(),
                cp.getVendor(),
                cp.getModel(),
                cp.getSerialNumber(),
                cp.getFirmwareVersion(),
                cp.getStatus(),
                cp.isOnline(),
                cp.getLastHeartbeat(),
                cp.getRegisteredAt(),
                cp.getErrorCode(),
                connectors
        );
    }

    public ConnectorDto toConnectorDto(ConnectorEntity c) {
        return new ConnectorDto(
                c.getConnectorId(),
                c.getStatus(),
                c.getCurrentPowerKw(),
                c.getCurrentAmps(),
                c.getVoltage(),
                c.getTemperatureCelsius(),
                c.getTotalEnergyKwh(),
                c.getErrorCode(),
                Boolean.TRUE.equals(c.getBlocked()),
                c.getBlockedReason(),
                c.getBlockedAt()
        );
    }
}
