package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.OffsetLimitPageable;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ChargingSessionEntity;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
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
 * Verifies that the pageable overload of {@link ChargingSessionRepository#search} applies
 * OFFSET and LIMIT at the DB level.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class ChargingSessionPaginationRepositoryTest {

    @Autowired
    ChargingSessionRepository repository;

    @Test
    void searchPageableLimitsResults() {
        Instant now = Instant.now();

        for (int i = 1; i <= 4; i++) {
            repository.save(ChargingSessionEntity.builder()
                    .transactionId(9000 + i).chargePointId("PAG_CPX").connectorId(1)
                    .startTime(now.minus(i, ChronoUnit.MINUTES))
                    .status(SessionStatus.Completed).build());
        }

        // limit=2, offset=0 → first 2 (most recent first due to ORDER BY startTime DESC)
        List<ChargingSessionEntity> page1 = repository.search(
                null, "PAG_CPX", null, null, new OffsetLimitPageable(0, 2));
        assertThat(page1).hasSize(2);

        // limit=2, offset=2 → next 2
        List<ChargingSessionEntity> page2 = repository.search(
                null, "PAG_CPX", null, null, new OffsetLimitPageable(2, 2));
        assertThat(page2).hasSize(2);

        // no overlap
        assertThat(page1).extracting(ChargingSessionEntity::getTransactionId)
                .doesNotContainAnyElementsOf(
                        page2.stream().map(ChargingSessionEntity::getTransactionId).toList());
    }

    @Test
    void searchPageableWithOffsetBeyondResultsReturnsEmpty() {
        Instant now = Instant.now();

        repository.save(ChargingSessionEntity.builder()
                .transactionId(9100).chargePointId("PAG_CPXZ").connectorId(1)
                .startTime(now).status(SessionStatus.Active).build());

        List<ChargingSessionEntity> result = repository.search(
                null, "PAG_CPXZ", null, null, new OffsetLimitPageable(10, 5));
        assertThat(result).isEmpty();
    }
}
