package com.accenture.nexcharge.simulator.service;

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
@Transactional
public class MeterService {

    private static final int DEFAULT_LOOKBACK_MINUTES = 60;

    private final MeterReadingRepository meterRepository;
    private final ChargePointRepository chargePointRepository;

    public List<MeterValueDto> findRecent(String chargePointId, Integer connectorId, Integer lastMinutes) {
        if (!chargePointRepository.existsById(chargePointId)) {
            throw new ChargePointNotFoundException(chargePointId);
        }

        int minutes = lastMinutes != null ? lastMinutes : DEFAULT_LOOKBACK_MINUTES;
        Instant after = Instant.now().minus(minutes, ChronoUnit.MINUTES);

        List<MeterReadingEntity> entities = (connectorId == null)
                ? meterRepository.findByChargePointIdAndTimestampAfterOrderByTimestampDesc(chargePointId, after)
                : meterRepository.findByChargePointIdAndConnectorIdAndTimestampAfterOrderByTimestampDesc(
                        chargePointId, connectorId, after);

        return entities.stream().map(this::toDto).toList();
    }

    public void save(MeterReadingEntity entity) {
        meterRepository.save(entity);
    }

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
