package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ConnectorEntity;
import com.accenture.nexcharge.simulator.model.enums.ConnectorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class ConnectorRepositoryTest {

    @Autowired
    ConnectorRepository repository;

    @Test
    void findsByChargePointId() {
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP1").connectorId(1).status(ConnectorStatus.Available).build());
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP1").connectorId(2).status(ConnectorStatus.Charging).build());
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP2").connectorId(1).status(ConnectorStatus.Available).build());

        List<ConnectorEntity> connectors = repository.findByChargePointIdOrderByConnectorIdAsc("CP1");
        assertThat(connectors).hasSize(2);
        assertThat(connectors.get(0).getConnectorId()).isEqualTo(1);
    }

    @Test
    void findsByChargePointAndConnector() {
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP1").connectorId(1).status(ConnectorStatus.Charging).build());

        Optional<ConnectorEntity> found = repository.findByChargePointIdAndConnectorId("CP1", 1);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ConnectorStatus.Charging);
    }

    @Test
    void countsByStatus() {
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP1").connectorId(1).status(ConnectorStatus.Charging).build());
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP2").connectorId(1).status(ConnectorStatus.Charging).build());
        repository.save(ConnectorEntity.builder()
                .chargePointId("CP3").connectorId(1).status(ConnectorStatus.Available).build());

        assertThat(repository.countByStatus(ConnectorStatus.Charging)).isEqualTo(2);
    }
}
