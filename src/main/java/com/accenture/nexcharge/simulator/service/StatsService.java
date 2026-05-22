package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final ChargePointRepository chargePointRepository;
    private final ConnectorRepository connectorRepository;
    private final ChargingSessionRepository sessionRepository;

    public StatsDto compute() {
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<ConnectorEntity> allConnectors = connectorRepository.findAll();
        double totalPowerKw = allConnectors.stream()
                .filter(c -> c.getStatus() == ConnectorStatus.Charging && c.getCurrentPowerKw() != null)
                .mapToDouble(ConnectorEntity::getCurrentPowerKw)
                .sum();

        BorneCounts counts = aggregateBorneCounts(allConnectors);

        List<ChargingSessionEntity> completedSessions = sessionRepository.findByStatus(SessionStatus.Completed);
        Long avgDurationMinutes = computeAverageDuration(completedSessions);
        Double avgEnergy = computeAverageEnergy(completedSessions);

        return new StatsDto(
                chargePointRepository.count(),
                chargePointRepository.countByOnline(true),
                counts.charging,
                counts.available,
                counts.faulted,
                sessionRepository.countByStatus(SessionStatus.Active),
                round1(totalPowerKw),
                round1(sessionRepository.sumEnergyDeliveredSince(startOfToday)),
                sessionRepository.countSince(startOfToday),
                sessionRepository.countSinceWithStatus(startOfToday, SessionStatus.Completed),
                avgDurationMinutes,
                avgEnergy
        );
    }

    /**
     * Roll connector statuses up to a single status per borne, with priority
     * Faulted &gt; Charging &gt; Available. A borne in any other connector state
     * (Preparing, Finishing, Unavailable, ...) is not counted toward the three
     * dashboard buckets.
     */
    private BorneCounts aggregateBorneCounts(List<ConnectorEntity> connectors) {
        Map<String, ConnectorStatus> rolledUp = new LinkedHashMap<>();
        for (ConnectorEntity c : connectors) {
            String borneId = c.getChargePointId();
            ConnectorStatus current = rolledUp.get(borneId);
            rolledUp.put(borneId, max(current, c.getStatus()));
        }
        long charging = 0;
        long available = 0;
        long faulted = 0;
        for (ConnectorStatus s : rolledUp.values()) {
            if (s == ConnectorStatus.Faulted) {
                faulted++;
            } else if (s == ConnectorStatus.Charging) {
                charging++;
            } else if (s == ConnectorStatus.Available) {
                available++;
            }
        }
        return new BorneCounts(charging, available, faulted);
    }

    private ConnectorStatus max(ConnectorStatus a, ConnectorStatus b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return rank(a) >= rank(b) ? a : b;
    }

    private int rank(ConnectorStatus s) {
        return switch (s) {
            case Faulted -> 3;
            case Charging -> 2;
            case Available -> 1;
            default -> 0;
        };
    }

    private record BorneCounts(long charging, long available, long faulted) {}

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
