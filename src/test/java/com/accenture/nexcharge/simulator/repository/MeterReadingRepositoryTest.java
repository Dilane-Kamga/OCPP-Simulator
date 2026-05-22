package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class MeterReadingRepositoryTest {

    @Autowired
    MeterReadingRepository repository;

    @Test
    void findsByChargePointSinceTimestamp() {
        Instant now = Instant.now();
        repository.save(MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(1).measurand("Power.Active.Import")
                .value(7000.0).unit("W").timestamp(now.minus(5, ChronoUnit.MINUTES)).build());
        repository.save(MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(1).measurand("Power.Active.Import")
                .value(7100.0).unit("W").timestamp(now.minus(30, ChronoUnit.MINUTES)).build());

        List<MeterReadingEntity> recent = repository.findByChargePointIdAndTimestampAfterOrderByTimestampDesc(
                "CP1", now.minus(10, ChronoUnit.MINUTES));
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getValue()).isEqualTo(7000.0);
    }

    @Test
    void filtersByConnectorId() {
        Instant now = Instant.now();
        repository.save(MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(1).measurand("Voltage")
                .value(230.0).unit("V").timestamp(now).build());
        repository.save(MeterReadingEntity.builder()
                .chargePointId("CP1").connectorId(2).measurand("Voltage")
                .value(231.0).unit("V").timestamp(now).build());

        List<MeterReadingEntity> connector1 = repository
                .findByChargePointIdAndConnectorIdAndTimestampAfterOrderByTimestampDesc(
                        "CP1", 1, now.minus(1, ChronoUnit.HOURS));
        assertThat(connector1).hasSize(1);
        assertThat(connector1.get(0).getValue()).isEqualTo(230.0);
    }
}
