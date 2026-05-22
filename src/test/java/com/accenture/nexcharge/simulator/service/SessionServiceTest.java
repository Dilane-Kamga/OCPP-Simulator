package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.SessionDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock ChargingSessionRepository repository;
    @InjectMocks SessionService service;

    @Test
    void searchByStatusOnly() {
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(1L).transactionId(1001).chargePointId("CP1").connectorId(1)
                .idTag("RFID-001")
                .startTime(Instant.parse("2026-05-22T10:00:00Z"))
                .meterStartWh(0.0)
                .status(SessionStatus.Active).build();
        when(repository.search(eq(SessionStatus.Active), eq(null), eq(null), eq(null),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(entity));

        List<SessionDto> result = service.search(SessionStatus.Active, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).transactionId()).isEqualTo(1001);
        assertThat(result.get(0).status()).isEqualTo(SessionStatus.Active);
    }

    @Test
    void durationMinutesComputedFromStartAndStop() {
        Instant start = Instant.parse("2026-05-22T10:00:00Z");
        Instant stop = start.plus(45, ChronoUnit.MINUTES);
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(1L).transactionId(1001).chargePointId("CP1").connectorId(1)
                .startTime(start).stopTime(stop)
                .meterStartWh(0.0).meterStopWh(5000.0).energyDeliveredKwh(5.0)
                .stopReason("Local").status(SessionStatus.Completed).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        SessionDto dto = service.getById(1L);
        assertThat(dto.durationMinutes()).isEqualTo(45L);
    }

    @Test
    void durationMinutesIsBasedOnNowWhenSessionActive() {
        Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(2L).transactionId(2001).chargePointId("CP1").connectorId(1)
                .startTime(start).stopTime(null)
                .status(SessionStatus.Active).build();
        when(repository.findById(2L)).thenReturn(Optional.of(entity));

        SessionDto dto = service.getById(2L);
        assertThat(dto.durationMinutes()).isBetween(9L, 11L);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void findActiveDelegatesToRepository() {
        ChargingSessionEntity entity = ChargingSessionEntity.builder()
                .id(1L).transactionId(1).chargePointId("CP1").connectorId(1)
                .startTime(Instant.now()).status(SessionStatus.Active).build();
        when(repository.findByStatus(SessionStatus.Active)).thenReturn(List.of(entity));

        List<SessionDto> result = service.findActive();
        assertThat(result).hasSize(1);
    }
}
