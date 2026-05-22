package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class ConnectorBlockRepositoryTest {

    @Autowired ConnectorRepository repository;

    @Test
    void blockedDefaultIsFalse() {
        ConnectorEntity saved = repository.save(ConnectorEntity.builder()
                .chargePointId("CP1").connectorId(1).status(ConnectorStatus.Available).build());

        Optional<ConnectorEntity> found = repository.findByChargePointIdAndConnectorId("CP1", 1);
        assertThat(found).isPresent();
        assertThat(found.get().getBlocked()).isFalse();
        assertThat(found.get().getBlockedReason()).isNull();
        assertThat(found.get().getBlockedAt()).isNull();
    }

    @Test
    void blockedFieldsRoundTrip() {
        Instant now = Instant.now();
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP2").connectorId(1)
                .status(ConnectorStatus.Charging)
                .blocked(true)
                .blockedReason("Quarterly maintenance")
                .blockedAt(now)
                .build());

        Optional<ConnectorEntity> found = repository.findByChargePointIdAndConnectorId("CP2", 1);
        assertThat(found).isPresent();
        ConnectorEntity entity = found.get();
        assertThat(entity.getBlocked()).isTrue();
        assertThat(entity.getBlockedReason()).isEqualTo("Quarterly maintenance");
        assertThat(entity.getBlockedAt()).isEqualTo(now);
    }

    @Test
    void countByBlockedCounts() {
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP3").connectorId(1)
                .status(ConnectorStatus.Available).blocked(true)
                .blockedReason("Test block").blockedAt(Instant.now()).build());
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP4").connectorId(1)
                .status(ConnectorStatus.Available).blocked(false).build());

        assertThat(repository.countByBlocked(true)).isEqualTo(1);
        assertThat(repository.countByBlocked(false)).isEqualTo(1);
    }

    @Test
    void clearingBlockFieldsPersists() {
        ConnectorEntity connector = repository.save(ConnectorEntity.builder()
                .chargePointId("CP5").connectorId(1)
                .status(ConnectorStatus.Charging)
                .blocked(true).blockedReason("Old block").blockedAt(Instant.now()).build());

        connector.setBlocked(false);
        connector.setBlockedReason(null);
        connector.setBlockedAt(null);
        repository.save(connector);

        Optional<ConnectorEntity> found = repository.findByChargePointIdAndConnectorId("CP5", 1);
        assertThat(found).isPresent();
        assertThat(found.get().getBlocked()).isFalse();
        assertThat(found.get().getBlockedReason()).isNull();
        assertThat(found.get().getBlockedAt()).isNull();
    }
}
