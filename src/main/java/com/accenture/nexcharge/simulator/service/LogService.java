package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.config.OffsetLimitPageable;
import com.accenture.nexcharge.simulator.model.dto.OcppLogDto;
import com.accenture.nexcharge.simulator.model.entity.OcppLogEntity;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.repository.OcppLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 1000;

    private final OcppLogRepository repository;

    @Transactional
    public void log(String chargePointId, LogDirection direction, String action, String payload) {
        repository.save(OcppLogEntity.builder()
                .chargePointId(chargePointId)
                .direction(direction)
                .action(action)
                .payload(payload)
                .timestamp(Instant.now())
                .build());
    }

    /** Backward-compatible overload: limit only, no offset. */
    public List<OcppLogDto> search(String chargePointId, String action, LogDirection direction,
                                   Integer lastMinutes, Integer limit) {
        return search(chargePointId, action, direction, lastMinutes, limit, 0);
    }

    public List<OcppLogDto> search(String chargePointId, String action, LogDirection direction,
                                   Integer lastMinutes, Integer limit, int offset) {
        Instant after = lastMinutes != null
                ? Instant.now().minus(lastMinutes, ChronoUnit.MINUTES)
                : null;
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.search(chargePointId, action, direction, after,
                        new OffsetLimitPageable(offset, safeLimit))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private OcppLogDto toDto(OcppLogEntity l) {
        return new OcppLogDto(
                l.getId(), l.getChargePointId(), l.getDirection(),
                l.getAction(), l.getPayload(), l.getTimestamp());
    }
}
