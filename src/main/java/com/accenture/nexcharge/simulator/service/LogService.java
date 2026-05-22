package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.OcppLogDto;
import com.accenture.nexcharge.simulator.model.entity.OcppLogEntity;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import com.accenture.nexcharge.simulator.repository.OcppLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LogService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final OcppLogRepository repository;

    public void log(String chargePointId, LogDirection direction, String action, String payload) {
        repository.save(OcppLogEntity.builder()
                .chargePointId(chargePointId)
                .direction(direction)
                .action(action)
                .payload(payload)
                .timestamp(Instant.now())
                .build());
    }

    public List<OcppLogDto> search(String chargePointId, String action, LogDirection direction,
                                   Integer lastMinutes, Integer limit) {
        Instant after = lastMinutes != null
                ? Instant.now().minus(lastMinutes, ChronoUnit.MINUTES)
                : null;
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.search(chargePointId, action, direction, after, PageRequest.of(0, safeLimit))
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
