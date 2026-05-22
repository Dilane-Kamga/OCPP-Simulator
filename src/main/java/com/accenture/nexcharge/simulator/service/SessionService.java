package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.config.OffsetLimitPageable;
import com.accenture.nexcharge.simulator.model.dto.SessionDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

    private static final int DEFAULT_LIMIT = 100;

    private final ChargingSessionRepository repository;

    /** Backward-compatible overload used by existing callers and tests. */
    public List<SessionDto> search(SessionStatus status, String chargePointId, Instant from, Instant to) {
        return search(status, chargePointId, from, to, DEFAULT_LIMIT, 0);
    }

    public List<SessionDto> search(SessionStatus status, String chargePointId,
                                   Instant from, Instant to, int limit, int offset) {
        return repository.search(status, chargePointId, from, to, new OffsetLimitPageable(offset, limit))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SessionDto> findActive() {
        return repository.findByStatus(SessionStatus.Active).stream()
                .map(this::toDto)
                .toList();
    }

    public SessionDto getById(Long id) {
        ChargingSessionEntity entity = repository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
        return toDto(entity);
    }

    private SessionDto toDto(ChargingSessionEntity s) {
        Long durationMinutes = computeDurationMinutes(s);
        return new SessionDto(
                s.getId(),
                s.getTransactionId(),
                s.getChargePointId(),
                s.getConnectorId(),
                s.getIdTag(),
                s.getStartTime(),
                s.getStopTime(),
                s.getMeterStartWh(),
                s.getMeterStopWh(),
                s.getEnergyDeliveredKwh(),
                s.getStopReason(),
                s.getStatus(),
                durationMinutes
        );
    }

    private Long computeDurationMinutes(ChargingSessionEntity s) {
        if (s.getStartTime() == null) {
            return null;
        }
        Instant end = s.getStopTime() != null ? s.getStopTime() : Instant.now();
        return Duration.between(s.getStartTime(), end).toMinutes();
    }
}
