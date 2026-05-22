package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.config.OffsetLimitPageable;
import com.accenture.nexcharge.simulator.model.dto.MeterValueDto;
import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.MeterReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeterService {

    private static final int DEFAULT_LOOKBACK_MINUTES = 60;
    private static final int DEFAULT_LIMIT = 100;

    private final MeterReadingRepository meterRepository;
    private final ChargePointRepository chargePointRepository;

    /** Backward-compatible overload used by existing callers and tests. */
    public List<MeterValueDto> findRecent(String chargePointId, Integer connectorId, Integer lastMinutes) {
        return findRecent(chargePointId, connectorId, lastMinutes, DEFAULT_LIMIT, 0);
    }

    public List<MeterValueDto> findRecent(String chargePointId, Integer connectorId,
                                          Integer lastMinutes, int limit, int offset) {
        if (!chargePointRepository.existsById(chargePointId)) {
            throw new ChargePointNotFoundException(chargePointId);
        }

        int minutes = lastMinutes != null ? lastMinutes : DEFAULT_LOOKBACK_MINUTES;
        Instant after = Instant.now().minus(minutes, ChronoUnit.MINUTES);

        OffsetLimitPageable pageable = new OffsetLimitPageable(offset, limit);
        List<MeterReadingEntity> entities = (connectorId == null)
                ? meterRepository.findByChargePointIdAndAfter(chargePointId, after, pageable)
                : meterRepository.findByChargePointIdAndConnectorIdAndAfter(
                        chargePointId, connectorId, after, pageable);

        return entities.stream().map(this::toDto).toList();
    }

    @Transactional
    public void save(MeterReadingEntity entity) {
        meterRepository.save(entity);
    }

    @Transactional
    public void saveAll(List<MeterReadingEntity> entities) {
        meterRepository.saveAll(entities);
    }

    private MeterValueDto toDto(MeterReadingEntity m) {
        return new MeterValueDto(
                m.getTimestamp(),
                m.getConnectorId(),
                m.getTransactionId(),
                m.getMeasurand(),
                m.getValue(),
                m.getUnit()
        );
    }
}
