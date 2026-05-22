package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.OcppLogEntity;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OcppLogRepository extends JpaRepository<OcppLogEntity, Long> {

    @Query("SELECT l FROM OcppLogEntity l WHERE " +
           "(:chargePointId IS NULL OR l.chargePointId = :chargePointId) AND " +
           "(:action IS NULL OR l.action = :action) AND " +
           "(:direction IS NULL OR l.direction = :direction) AND " +
           "(:after IS NULL OR l.timestamp >= :after) " +
           "ORDER BY l.timestamp DESC")
    List<OcppLogEntity> search(
            @Param("chargePointId") String chargePointId,
            @Param("action") String action,
            @Param("direction") LogDirection direction,
            @Param("after") Instant after,
            Pageable pageable
    );
}
