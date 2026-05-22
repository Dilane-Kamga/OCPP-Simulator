package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargePointRepository extends JpaRepository<ChargePointEntity, String> {
    List<ChargePointEntity> findByStatus(ChargePointStatus status);
    long countByOnline(boolean online);
    long countByStatus(ChargePointStatus status);
}
