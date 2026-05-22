package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final ChargePointRepository chargePointRepository;
    private final ConnectorRepository connectorRepository;
    private final ChargingSessionRepository sessionRepository;

    public StatsDto compute() {
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);

        double totalPowerKw = connectorRepository.findAll().stream()
                .filter(c -> c.getStatus() == ConnectorStatus.Charging && c.getCurrentPowerKw() != null)
                .mapToDouble(c -> c.getCurrentPowerKw())
                .sum();

        List<ChargingSessionEntity> completedSessions = sessionRepository.findByStatus(SessionStatus.Completed);

        Long avgDurationMinutes = computeAverageDuration(completedSessions);
        Double avgEnergy = computeAverageEnergy(completedSessions);

        return new StatsDto(
                chargePointRepository.count(),
                chargePointRepository.countByOnline(true),
                chargePointRepository.countByStatus(ChargePointStatus.Charging),
                chargePointRepository.countByStatus(ChargePointStatus.Available),
                chargePointRepository.countByStatus(ChargePointStatus.Faulted),
                sessionRepository.countByStatus(SessionStatus.Active),
                round1(totalPowerKw),
                round1(sessionRepository.sumEnergyDeliveredSince(startOfToday)),
                sessionRepository.countSince(startOfToday),
                sessionRepository.countSinceWithStatus(startOfToday, SessionStatus.Completed),
                avgDurationMinutes,
                avgEnergy
        );
    }

    private Long computeAverageDuration(List<ChargingSessionEntity> sessions) {
        if (sessions.isEmpty()) {
            return null;
        }
        long totalMinutes = sessions.stream()
                .filter(s -> s.getStartTime() != null && s.getStopTime() != null)
                .mapToLong(s -> Duration.between(s.getStartTime(), s.getStopTime()).toMinutes())
                .sum();
        long count = sessions.stream()
                .filter(s -> s.getStartTime() != null && s.getStopTime() != null)
                .count();
        return count == 0 ? null : totalMinutes / count;
    }

    private Double computeAverageEnergy(List<ChargingSessionEntity> sessions) {
        if (sessions.isEmpty()) {
            return null;
        }
        double total = sessions.stream()
                .filter(s -> s.getEnergyDeliveredKwh() != null)
                .mapToDouble(ChargingSessionEntity::getEnergyDeliveredKwh)
                .sum();
        long count = sessions.stream()
                .filter(s -> s.getEnergyDeliveredKwh() != null)
                .count();
        return count == 0 ? null : round1(total / count);
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
