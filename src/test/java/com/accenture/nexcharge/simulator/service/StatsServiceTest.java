package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.repository.ChargePointRepository;
import com.accenture.nexcharge.simulator.repository.ChargingSessionRepository;
import com.accenture.nexcharge.simulator.repository.ConnectorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock ChargePointRepository chargePointRepository;
    @Mock ConnectorRepository connectorRepository;
    @Mock ChargingSessionRepository sessionRepository;

    @InjectMocks StatsService service;

    @Test
    void aggregatesAllMetrics() {
        when(chargePointRepository.count()).thenReturn(5L);
        when(chargePointRepository.countByOnline(true)).thenReturn(4L);
        when(chargePointRepository.countByStatus(ChargePointStatus.Charging)).thenReturn(2L);
        when(chargePointRepository.countByStatus(ChargePointStatus.Available)).thenReturn(2L);
        when(chargePointRepository.countByStatus(ChargePointStatus.Faulted)).thenReturn(1L);
        when(sessionRepository.countByStatus(SessionStatus.Active)).thenReturn(2L);

        ConnectorEntity charging1 = ConnectorEntity.builder()
                .currentPowerKw(7.2).status(ConnectorStatus.Charging).build();
        ConnectorEntity charging2 = ConnectorEntity.builder()
                .currentPowerKw(22.0).status(ConnectorStatus.Charging).build();
        when(connectorRepository.findAll()).thenReturn(List.of(charging1, charging2));

        when(sessionRepository.countSince(any())).thenReturn(8L);
        when(sessionRepository.countSinceWithStatus(any(), org.mockito.ArgumentMatchers.eq(SessionStatus.Completed)))
                .thenReturn(6L);
        when(sessionRepository.sumEnergyDeliveredSince(any())).thenReturn(63.6);

        ChargingSessionEntity completed = ChargingSessionEntity.builder()
                .startTime(Instant.parse("2026-05-22T08:00:00Z"))
                .stopTime(Instant.parse("2026-05-22T09:35:00Z"))
                .energyDeliveredKwh(18.5)
                .status(SessionStatus.Completed).build();
        when(sessionRepository.findByStatus(SessionStatus.Completed))
                .thenReturn(List.of(completed));

        StatsDto stats = service.compute();
        assertThat(stats.totalChargePoints()).isEqualTo(5);
        assertThat(stats.onlineChargePoints()).isEqualTo(4);
        assertThat(stats.chargingNow()).isEqualTo(2);
        assertThat(stats.availableNow()).isEqualTo(2);
        assertThat(stats.faultedNow()).isEqualTo(1);
        assertThat(stats.activeSessionsCount()).isEqualTo(2);
        assertThat(stats.totalPowerKw()).isEqualTo(29.2);
        assertThat(stats.todayEnergyKwh()).isEqualTo(63.6);
        assertThat(stats.todaySessionsCount()).isEqualTo(8);
        assertThat(stats.todaySessionsCompleted()).isEqualTo(6);
        assertThat(stats.averageSessionDurationMinutes()).isEqualTo(95L);
        assertThat(stats.averageEnergyPerSessionKwh()).isEqualTo(18.5);
    }

    @Test
    void averageNullsWhenNoCompletedSessions() {
        when(chargePointRepository.count()).thenReturn(0L);
        when(chargePointRepository.countByOnline(true)).thenReturn(0L);
        when(chargePointRepository.countByStatus(any())).thenReturn(0L);
        when(sessionRepository.countByStatus(any())).thenReturn(0L);
        when(connectorRepository.findAll()).thenReturn(List.of());
        when(sessionRepository.countSince(any())).thenReturn(0L);
        when(sessionRepository.countSinceWithStatus(any(), any())).thenReturn(0L);
        when(sessionRepository.sumEnergyDeliveredSince(any())).thenReturn(0.0);
        when(sessionRepository.findByStatus(SessionStatus.Completed)).thenReturn(List.of());

        StatsDto stats = service.compute();
        assertThat(stats.averageSessionDurationMinutes()).isNull();
        assertThat(stats.averageEnergyPerSessionKwh()).isNull();
    }
}
