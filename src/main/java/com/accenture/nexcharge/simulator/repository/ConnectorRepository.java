package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectorRepository extends JpaRepository<ConnectorEntity, Long> {
    List<ConnectorEntity> findByChargePointIdOrderByConnectorIdAsc(String chargePointId);
    Optional<ConnectorEntity> findByChargePointIdAndConnectorId(String chargePointId, Integer connectorId);
    long countByStatus(ConnectorStatus status);
    long countByBlocked(Boolean blocked);
}
