package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.MeterValueDto;
import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.MeterReadingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeterServiceTest {

    @Mock MeterReadingRepository meterRepository;
    @Mock ChargePointRepository chargePointRepository;
    @InjectMocks MeterService service;

    @Test
    void findRecentDelegatesToRepository() {
        when(chargePointRepository.existsById("CP1")).thenReturn(true);
        MeterReadingEntity entity = MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(1).transactionId(1001)
                .measurand("Power.Active.Import").value(7000.0).unit("W")
                .timestamp(Instant.now()).build();
        when(meterRepository.findByChargePointIdAndAfter(eq("CP1"), any(), any()))
                .thenReturn(List.of(entity));

        List<MeterValueDto> result = service.findRecent("CP1", null, 60);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).measurand()).isEqualTo("Power.Active.Import");
    }

    @Test
    void findRecentFiltersByConnector() {
        when(chargePointRepository.existsById("CP1")).thenReturn(true);
        when(meterRepository.findByChargePointIdAndConnectorIdAndAfter(
                eq("CP1"), eq(1), any(), any())).thenReturn(List.of());

        service.findRecent("CP1", 1, 60);

        ArgumentCaptor<Instant> after = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(meterRepository)
                .findByChargePointIdAndConnectorIdAndAfter(
                        eq("CP1"), eq(1), after.capture(), any());
        assertThat(after.getValue()).isBefore(Instant.now());
    }

    @Test
    void findRecentThrowsForUnknownChargePoint() {
        when(chargePointRepository.existsById("UNKNOWN")).thenReturn(false);
        assertThatThrownBy(() -> service.findRecent("UNKNOWN", null, 60))
                .isInstanceOf(ChargePointNotFoundException.class);
    }
}
