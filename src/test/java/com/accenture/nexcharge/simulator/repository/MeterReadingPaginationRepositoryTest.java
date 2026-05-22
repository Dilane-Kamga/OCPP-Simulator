package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.OffsetLimitPageable;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.MeterReadingEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the pageable overloads of {@link MeterReadingRepository} actually
 * apply OFFSET and LIMIT at the DB level, not in Java.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class MeterReadingPaginationRepositoryTest {

    @Autowired
    MeterReadingRepository repository;

    @Test
    void pageableQueryLimitsResults() {
        Instant now = Instant.now();
        Instant after = now.minus(10, ChronoUnit.MINUTES);

        for (int i = 1; i <= 5; i++) {
            repository.save(MeterReadingEntity.builder()
                    .chargePointId("PAG_CP1").connectorId(1)
                    .measurand("Power.Active.Import")
                    .value((double) i * 100).unit("W")
                    .timestamp(now.minus(i, ChronoUnit.SECONDS))
                    .build());
        }

        List<MeterReadingEntity> page = repository.findByChargePointIdAndAfter(
                "PAG_CP1", after, new OffsetLimitPageable(0, 2));

        assertThat(page).hasSize(2);
    }

    @Test
    void pageableQueryAppliesOffset() {
        Instant now = Instant.now();
        Instant after = now.minus(10, ChronoUnit.MINUTES);

        // Insert 4 readings with distinct values 100, 200, 300, 400 (descending by timestamp)
        for (int i = 1; i <= 4; i++) {
            repository.save(MeterReadingEntity.builder()
                    .chargePointId("PAG_CP2").connectorId(1)
                    .measurand("Power.Active.Import")
                    .value((double) i * 100).unit("W")
                    .timestamp(now.minus(i, ChronoUnit.SECONDS))
                    .build());
        }

        // Order is DESC by timestamp: 100 is newest (1s ago) → first; 400 is oldest → last
        // offset=0, limit=2 → [100.0, 200.0]
        List<MeterReadingEntity> firstPage = repository.findByChargePointIdAndAfter(
                "PAG_CP2", after, new OffsetLimitPageable(0, 2));
        assertThat(firstPage).extracting(MeterReadingEntity::getValue)
                .containsExactly(100.0, 200.0);

        // offset=2, limit=2 → [300.0, 400.0]
        List<MeterReadingEntity> secondPage = repository.findByChargePointIdAndAfter(
                "PAG_CP2", after, new OffsetLimitPageable(2, 2));
        assertThat(secondPage).extracting(MeterReadingEntity::getValue)
                .containsExactly(300.0, 400.0);
    }

    @Test
    void connectorPageableQueryLimitsResults() {
        Instant now = Instant.now();
        Instant after = now.minus(10, ChronoUnit.MINUTES);

        for (int i = 1; i <= 3; i++) {
            repository.save(MeterReadingEntity.builder()
                    .chargePointId("PAG_CP3").connectorId(2)
                    .measurand("Voltage")
                    .value(230.0 + i).unit("V")
                    .timestamp(now.minus(i, ChronoUnit.SECONDS))
                    .build());
        }

        List<MeterReadingEntity> page = repository.findByChargePointIdAndConnectorIdAndAfter(
                "PAG_CP3", 2, after, new OffsetLimitPageable(0, 2));

        assertThat(page).hasSize(2);
    }
}
