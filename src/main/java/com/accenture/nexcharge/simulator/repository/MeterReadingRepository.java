package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MeterReadingRepository extends JpaRepository<MeterReadingEntity, Long> {
    List<MeterReadingEntity> findByChargePointIdAndTimestampAfterOrderByTimestampDesc(
            String chargePointId, Instant after);

    List<MeterReadingEntity> findByChargePointIdAndConnectorIdAndTimestampAfterOrderByTimestampDesc(
            String chargePointId, Integer connectorId, Instant after);
}
