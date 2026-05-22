package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
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
        when(sessionRepository.countByStatus(SessionStatus.Active)).thenReturn(2L);

        // 5 bornes: A and B charging (1 connector each), C faulted (1 connector),
        // D and E available (D has 1 connector, E has 2 connectors).
        ConnectorEntity a1 = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .currentPowerKw(7.2).status(ConnectorStatus.Charging).build();
        ConnectorEntity b1 = ConnectorEntity.builder()
                .chargePointId("BORNE_B").connectorId(1)
                .currentPowerKw(22.0).status(ConnectorStatus.Charging).build();
        ConnectorEntity c1 = ConnectorEntity.builder()
                .chargePointId("BORNE_C").connectorId(1)
                .currentPowerKw(0.0).status(ConnectorStatus.Faulted).build();
        ConnectorEntity d1 = ConnectorEntity.builder()
                .chargePointId("BORNE_D").connectorId(1)
                .status(ConnectorStatus.Available).build();
        ConnectorEntity e1 = ConnectorEntity.builder()
                .chargePointId("BORNE_E").connectorId(1)
                .status(ConnectorStatus.Available).build();
        ConnectorEntity e2 = ConnectorEntity.builder()
                .chargePointId("BORNE_E").connectorId(2)
                .status(ConnectorStatus.Available).build();
        when(connectorRepository.findAll()).thenReturn(List.of(a1, b1, c1, d1, e1, e2));
        when(connectorRepository.countByBlocked(true)).thenReturn(0L);

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
        when(sessionRepository.countByStatus(any())).thenReturn(0L);
        when(connectorRepository.findAll()).thenReturn(List.of());
        when(connectorRepository.countByBlocked(true)).thenReturn(0L);
        when(sessionRepository.countSince(any())).thenReturn(0L);
        when(sessionRepository.countSinceWithStatus(any(), any())).thenReturn(0L);
        when(sessionRepository.sumEnergyDeliveredSince(any())).thenReturn(0.0);
        when(sessionRepository.findByStatus(SessionStatus.Completed)).thenReturn(List.of());

        StatsDto stats = service.compute();
        assertThat(stats.averageSessionDurationMinutes()).isNull();
        assertThat(stats.averageEnergyPerSessionKwh()).isNull();
    }

    @Test
    void faultedConnectorMakesBornFaultedEvenIfOtherConnectorIsCharging() {
        // Multi-connector borne where one connector charges and another is faulted -> borne is Faulted
        when(chargePointRepository.count()).thenReturn(1L);
        when(chargePointRepository.countByOnline(true)).thenReturn(1L);
        when(sessionRepository.countByStatus(any())).thenReturn(1L);
        ConnectorEntity ch = ConnectorEntity.builder()
                .chargePointId("BORNE_X").connectorId(1)
                .currentPowerKw(7.0).status(ConnectorStatus.Charging).build();
        ConnectorEntity ft = ConnectorEntity.builder()
                .chargePointId("BORNE_X").connectorId(2)
                .status(ConnectorStatus.Faulted).build();
        when(connectorRepository.findAll()).thenReturn(List.of(ch, ft));
        when(connectorRepository.countByBlocked(true)).thenReturn(0L);
        when(sessionRepository.countSince(any())).thenReturn(0L);
        when(sessionRepository.countSinceWithStatus(any(), any())).thenReturn(0L);
        when(sessionRepository.sumEnergyDeliveredSince(any())).thenReturn(0.0);
        when(sessionRepository.findByStatus(SessionStatus.Completed)).thenReturn(List.of());

        StatsDto stats = service.compute();
        assertThat(stats.faultedNow()).isEqualTo(1);
        assertThat(stats.chargingNow()).isEqualTo(0);
        assertThat(stats.availableNow()).isEqualTo(0);
    }

    @Test
    void blockedNowCountsBlockedConnectors() {
        when(chargePointRepository.count()).thenReturn(2L);
        when(chargePointRepository.countByOnline(true)).thenReturn(2L);
        when(sessionRepository.countByStatus(any())).thenReturn(0L);
        ConnectorEntity blocked = ConnectorEntity.builder()
                .chargePointId("BORNE_A").connectorId(1)
                .status(ConnectorStatus.Available).blocked(true).build();
        ConnectorEntity normal = ConnectorEntity.builder()
                .chargePointId("BORNE_B").connectorId(1)
                .status(ConnectorStatus.Available).blocked(false).build();
        when(connectorRepository.findAll()).thenReturn(List.of(blocked, normal));
        when(connectorRepository.countByBlocked(true)).thenReturn(1L);
        when(sessionRepository.countSince(any())).thenReturn(0L);
        when(sessionRepository.countSinceWithStatus(any(), any())).thenReturn(0L);
        when(sessionRepository.sumEnergyDeliveredSince(any())).thenReturn(0.0);
        when(sessionRepository.findByStatus(SessionStatus.Completed)).thenReturn(List.of());

        StatsDto stats = service.compute();
        assertThat(stats.blockedNow()).isEqualTo(1);
    }
}
