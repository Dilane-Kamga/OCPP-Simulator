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
        repository.save(ChargingSessionEntity.builder()
                .transactionId(1001).chargePointId("CP1").connectorId(1)
                .startTime(Instant.now()).status(SessionStatus.Active).build());

        Optional<ChargingSessionEntity> found = repository.findByTransactionId(1001);
        assertThat(found).isPresent();
        assertThat(found.get().getChargePointId()).isEqualTo("CP1");
    }

    @Test
    void findsActiveSessions() {
        repository.save(ChargingSessionEntity.builder()
                .transactionId(2001).chargePointId("CP1").connectorId(1)
                .startTime(Instant.now()).status(SessionStatus.Active).build());
        repository.save(ChargingSessionEntity.builder()
                .transactionId(2002).chargePointId("CP2").connectorId(1)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .stopTime(Instant.now())
                .status(SessionStatus.Completed).build());

        List<ChargingSessionEntity> active = repository.findByStatus(SessionStatus.Active);
        assertThat(active).extracting(ChargingSessionEntity::getTransactionId).containsExactly(2001);
    }

    @Test
    void findsBetweenDates() {
        Instant from = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant to = Instant.now();
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
}
