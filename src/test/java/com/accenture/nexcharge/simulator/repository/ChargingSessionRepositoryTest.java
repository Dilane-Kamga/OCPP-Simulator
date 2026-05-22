package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class ChargingSessionRepositoryTest {

    @Autowired
    ChargingSessionRepository repository;

    @Test
    void findsByTransactionId() {
        Instant now = Instant.now();
        repository.save(ChargingSessionEntity.builder()
                .transactionId(1001).chargePointId("CP1").connectorId(1)
                .startTime(now).status(SessionStatus.Active).build());

        Optional<ChargingSessionEntity> found = repository.findByTransactionId(1001);
        assertThat(found).isPresent();
        assertThat(found.get().getChargePointId()).isEqualTo("CP1");
    }

    @Test
    void findsActiveSessions() {
        Instant now = Instant.now();
        repository.save(ChargingSessionEntity.builder()
                .transactionId(2001).chargePointId("CP1").connectorId(1)
                .startTime(now).status(SessionStatus.Active).build());
        repository.save(ChargingSessionEntity.builder()
                .transactionId(2002).chargePointId("CP2").connectorId(1)
                .startTime(now.minus(1, ChronoUnit.HOURS))
                .stopTime(now)
                .status(SessionStatus.Completed).build());

        List<ChargingSessionEntity> active = repository.findByStatus(SessionStatus.Active);
        assertThat(active).extracting(ChargingSessionEntity::getTransactionId).containsExactly(2001);
    }

    @Test
    void findsBetweenDates() {
        Instant now = Instant.now();
        Instant from = now.minus(2, ChronoUnit.HOURS);
        Instant to = now;
        repository.save(ChargingSessionEntity.builder()
                .transactionId(3001).chargePointId("CP1").connectorId(1)
                .startTime(from.plus(30, ChronoUnit.MINUTES))
                .status(SessionStatus.Active).build());
        repository.save(ChargingSessionEntity.builder()
                .transactionId(3002).chargePointId("CP1").connectorId(1)
                .startTime(from.minus(1, ChronoUnit.HOURS))
                .status(SessionStatus.Completed).build());

        List<ChargingSessionEntity> result = repository.findByStartTimeBetween(from, to);
        assertThat(result).extracting(ChargingSessionEntity::getTransactionId).containsExactly(3001);
    }

    @Test
    void searchAppliesAllFilters() {
        Instant now = Instant.now();
        // matches: status=Active, CP1, in date window
        repository.save(ChargingSessionEntity.builder()
                .transactionId(4001).chargePointId("CP1").connectorId(1)
                .startTime(now.minus(30, ChronoUnit.MINUTES))
                .status(SessionStatus.Active).build());
        // wrong status
        repository.save(ChargingSessionEntity.builder()
                .transactionId(4002).chargePointId("CP1").connectorId(1)
                .startTime(now.minus(30, ChronoUnit.MINUTES))
                .status(SessionStatus.Completed).build());
        // wrong charge point
        repository.save(ChargingSessionEntity.builder()
                .transactionId(4003).chargePointId("CP2").connectorId(1)
                .startTime(now.minus(30, ChronoUnit.MINUTES))
                .status(SessionStatus.Active).build());

        List<ChargingSessionEntity> result = repository.search(
                SessionStatus.Active, "CP1",
                now.minus(2, ChronoUnit.HOURS), now);

        assertThat(result).extracting(ChargingSessionEntity::getTransactionId).containsExactly(4001);
    }

    @Test
    void searchWithAllNullFiltersReturnsAll() {
        Instant now = Instant.now();
        repository.save(ChargingSessionEntity.builder()
                .transactionId(5001).chargePointId("CP1").connectorId(1)
                .startTime(now).status(SessionStatus.Active).build());
        repository.save(ChargingSessionEntity.builder()
                .transactionId(5002).chargePointId("CP2").connectorId(1)
                .startTime(now.minus(1, ChronoUnit.HOURS))
                .status(SessionStatus.Completed).build());
        repository.save(ChargingSessionEntity.builder()
                .transactionId(5003).chargePointId("CP3").connectorId(1)
                .startTime(now.minus(2, ChronoUnit.HOURS))
                .status(SessionStatus.Error).build());

        List<ChargingSessionEntity> result = repository.search(null, null, null, null);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ChargingSessionEntity::getTransactionId)
                .containsExactlyInAnyOrder(5001, 5002, 5003);
    }

    @Test
    void searchRespectsDateRangeBoundary() {
        Instant now = Instant.now();
        Instant from = now.minus(1, ChronoUnit.HOURS);
        Instant to = now;
        // before range
        repository.save(ChargingSessionEntity.builder()
                .transactionId(6001).chargePointId("CP1").connectorId(1)
                .startTime(from.minus(10, ChronoUnit.MINUTES))
                .status(SessionStatus.Active).build());
        // exactly at lower bound (inclusive)
        repository.save(ChargingSessionEntity.builder()
                .transactionId(6002).chargePointId("CP1").connectorId(1)
                .startTime(from)
                .status(SessionStatus.Active).build());
        // after range
        repository.save(ChargingSessionEntity.builder()
                .transactionId(6003).chargePointId("CP1").connectorId(1)
                .startTime(to.plus(10, ChronoUnit.MINUTES))
                .status(SessionStatus.Active).build());

        List<ChargingSessionEntity> result = repository.search(null, null, from, to);

        assertThat(result).extracting(ChargingSessionEntity::getTransactionId).containsExactly(6002);
    }

    @Test
    void countSinceAndCountSinceWithStatus() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(1, ChronoUnit.HOURS);
        // Within window, Active
        repository.save(ChargingSessionEntity.builder()
                .transactionId(7001).chargePointId("CP1").connectorId(1)
                .startTime(now.minus(30, ChronoUnit.MINUTES))
                .status(SessionStatus.Active).build());
        // Within window, Completed
        repository.save(ChargingSessionEntity.builder()
                .transactionId(7002).chargePointId("CP1").connectorId(1)
                .startTime(now.minus(15, ChronoUnit.MINUTES))
                .status(SessionStatus.Completed).build());
        // Outside window
        repository.save(ChargingSessionEntity.builder()
                .transactionId(7003).chargePointId("CP1").connectorId(1)
                .startTime(now.minus(2, ChronoUnit.HOURS))
                .status(SessionStatus.Active).build());

        assertThat(repository.countSince(cutoff)).isEqualTo(2L);
        assertThat(repository.countSinceWithStatus(cutoff, SessionStatus.Active)).isEqualTo(1L);
        assertThat(repository.countSinceWithStatus(cutoff, SessionStatus.Completed)).isEqualTo(1L);
    }

    @Test
    void sumEnergyDeliveredSinceAndZeroCase() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(1, ChronoUnit.HOURS);

        // Zero-row case: no rows ? COALESCE returns 0
        assertThat(repository.sumEnergyDeliveredSince(cutoff)).isEqualTo(0.0);

        repository.save(ChargingSessionEntity.builder()
                .transactionId(8001).chargePointId("CP1").connectorId(1)
                .startTime(now.minus(30, ChronoUnit.MINUTES))
                .energyDeliveredKwh(12.5)
                .status(SessionStatus.Completed).build());
        repository.save(ChargingSessionEntity.builder()
                .transactionId(8002).chargePointId("CP1").connectorId(1)
                .startTime(now.minus(10, ChronoUnit.MINUTES))
                .energyDeliveredKwh(7.5)
                .status(SessionStatus.Completed).build());
        // outside window — must NOT contribute
        repository.save(ChargingSessionEntity.builder()
                .transactionId(8003).chargePointId("CP1").connectorId(1)
                .startTime(now.minus(3, ChronoUnit.HOURS))
                .energyDeliveredKwh(100.0)
                .status(SessionStatus.Completed).build());

        assertThat(repository.sumEnergyDeliveredSince(cutoff)).isEqualTo(20.0);
    }
}
