package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChargingSessionRepository extends JpaRepository<ChargingSessionEntity, Long> {
    Optional<ChargingSessionEntity> findByTransactionId(Integer transactionId);
    List<ChargingSessionEntity> findByStatus(SessionStatus status);
    List<ChargingSessionEntity> findByChargePointId(String chargePointId);
    List<ChargingSessionEntity> findByStartTimeBetween(Instant from, Instant to);
    long countByStatus(SessionStatus status);

    @Query("SELECT s FROM ChargingSessionEntity s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:chargePointId IS NULL OR s.chargePointId = :chargePointId) AND " +
           "(:from IS NULL OR s.startTime >= :from) AND " +
           "(:to IS NULL OR s.startTime <= :to) " +
           "ORDER BY s.startTime DESC")
    List<ChargingSessionEntity> search(
            @Param("status") SessionStatus status,
            @Param("chargePointId") String chargePointId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT s FROM ChargingSessionEntity s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:chargePointId IS NULL OR s.chargePointId = :chargePointId) AND " +
           "(:from IS NULL OR s.startTime >= :from) AND " +
           "(:to IS NULL OR s.startTime <= :to) " +
           "ORDER BY s.startTime DESC")
    List<ChargingSessionEntity> search(
            @Param("status") SessionStatus status,
            @Param("chargePointId") String chargePointId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("SELECT COUNT(s) FROM ChargingSessionEntity s WHERE s.startTime >= :from")
    long countSince(@Param("from") Instant from);

    @Query("SELECT COALESCE(SUM(s.energyDeliveredKwh), 0) FROM ChargingSessionEntity s WHERE s.startTime >= :from")
    double sumEnergyDeliveredSince(@Param("from") Instant from);

    @Query("SELECT COUNT(s) FROM ChargingSessionEntity s WHERE s.startTime >= :from AND s.status = :status")
    long countSinceWithStatus(@Param("from") Instant from, @Param("status") SessionStatus status);
}
