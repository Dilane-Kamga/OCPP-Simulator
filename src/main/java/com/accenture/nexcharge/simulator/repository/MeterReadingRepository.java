package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MeterReadingRepository extends JpaRepository<MeterReadingEntity, Long> {
    List<MeterReadingEntity> findByChargePointIdAndTimestampAfterOrderByTimestampDesc(
            String chargePointId, Instant after);

    List<MeterReadingEntity> findByChargePointIdAndConnectorIdAndTimestampAfterOrderByTimestampDesc(
            String chargePointId, Integer connectorId, Instant after);

    @Query("SELECT m FROM MeterReadingEntity m WHERE m.chargePointId = :chargePointId " +
           "AND m.timestamp > :after ORDER BY m.timestamp DESC")
    List<MeterReadingEntity> findByChargePointIdAndAfter(
            @Param("chargePointId") String chargePointId,
            @Param("after") Instant after,
            Pageable pageable);

    @Query("SELECT m FROM MeterReadingEntity m WHERE m.chargePointId = :chargePointId " +
           "AND m.connectorId = :connectorId AND m.timestamp > :after ORDER BY m.timestamp DESC")
    List<MeterReadingEntity> findByChargePointIdAndConnectorIdAndAfter(
            @Param("chargePointId") String chargePointId,
            @Param("connectorId") Integer connectorId,
            @Param("after") Instant after,
            Pageable pageable);
}
