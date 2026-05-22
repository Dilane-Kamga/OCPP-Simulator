package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.OcppLogEntity;
import com.accenture.nexcharge.simulator.model.enums.LogDirection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class OcppLogRepositoryTest {

    @Autowired
    OcppLogRepository repository;

    @Test
    void searchByMultipleFilters() {
        Instant now = Instant.now();
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP1").direction(LogDirection.IN).action("BootNotification")
                .payload("{}").timestamp(now).build());
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP1").direction(LogDirection.IN).action("Heartbeat")
                .payload("{}").timestamp(now.minus(2, ChronoUnit.MINUTES)).build());
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP2").direction(LogDirection.OUT).action("RemoteStart")
                .payload("{}").timestamp(now).build());

        List<OcppLogEntity> result = repository.search(
                "CP1", "Heartbeat", LogDirection.IN, now.minus(10, ChronoUnit.MINUTES),
                PageRequest.of(0, 10));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("Heartbeat");
    }

    @Test
    void searchWithNoFilters() {
        Instant now = Instant.now();
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP1").direction(LogDirection.IN).action("BootNotification")
                .payload("{}").timestamp(now).build());
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP2").direction(LogDirection.OUT).action("RemoteStart")
                .payload("{}").timestamp(now.minus(1, ChronoUnit.MINUTES)).build());
        repository.save(OcppLogEntity.builder()
                .chargePointId("CP3").direction(LogDirection.IN).action("Heartbeat")
                .payload("{}").timestamp(now.minus(2, ChronoUnit.MINUTES)).build());

        List<OcppLogEntity> result = repository.search(null, null, null, null, PageRequest.of(0, 100));
        assertThat(result).hasSize(3);
        assertThat(result).extracting(OcppLogEntity::getChargePointId)
                .containsExactlyInAnyOrder("CP1", "CP2", "CP3");
    }
}
