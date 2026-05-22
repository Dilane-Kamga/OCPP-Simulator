package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.ChargePointEntity;
import com.accenture.nexcharge.simulator.model.enums.ChargePointStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnableConfigurationProperties({SimulatorProperties.class, OcppProperties.class})
class ChargePointRepositoryTest {

    @Autowired
    ChargePointRepository repository;

    @Test
    void savesAndLoadsByPrimaryKey() {
        ChargePointEntity cp = ChargePointEntity.builder()
                .chargePointId("BORNE_TEST")
                .vendor("Legrand")
                .model("Green'Up Premium")
                .serialNumber("LGR-TEST")
                .firmwareVersion("1.0.0")
                .status(ChargePointStatus.Available)
                .online(true)
                .lastHeartbeat(Instant.now())
                .registeredAt(Instant.now())
                .errorCode("NoError")
                .build();
        repository.save(cp);

        Optional<ChargePointEntity> found = repository.findById("BORNE_TEST");
        assertThat(found).isPresent();
        assertThat(found.get().getVendor()).isEqualTo("Legrand");
    }

    @Test
    void findsByStatus() {
        repository.save(ChargePointEntity.builder()
                .chargePointId("BORNE_X").status(ChargePointStatus.Charging).build());
        repository.save(ChargePointEntity.builder()
                .chargePointId("BORNE_Y").status(ChargePointStatus.Available).build());

        List<ChargePointEntity> charging = repository.findByStatus(ChargePointStatus.Charging);
        assertThat(charging).extracting(ChargePointEntity::getChargePointId).containsExactly("BORNE_X");
    }

    @Test
    void countsOnline() {
        repository.save(ChargePointEntity.builder()
                .chargePointId("ON_1").online(true).build());
        repository.save(ChargePointEntity.builder()
                .chargePointId("ON_2").online(true).build());
        repository.save(ChargePointEntity.builder()
                .chargePointId("OFF_1").online(false).build());

        assertThat(repository.countByOnline(true)).isEqualTo(2);
    }
}
