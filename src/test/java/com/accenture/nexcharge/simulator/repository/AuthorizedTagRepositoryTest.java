package com.accenture.nexcharge.simulator.repository;

import com.accenture.nexcharge.simulator.config.OcppProperties;
import com.accenture.nexcharge.simulator.config.SimulatorProperties;
import com.accenture.nexcharge.simulator.model.entity.AuthorizedTagEntity;
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
class AuthorizedTagRepositoryTest {

    @Autowired
    AuthorizedTagRepository repository;

    @Test
    void savesAndLoadsById() {
        AuthorizedTagEntity tag = AuthorizedTagEntity.builder()
                .idTag("RFID-TEST-01")
                .blocked(false)
                .createdAt(Instant.now())
                .build();
        repository.save(tag);

        Optional<AuthorizedTagEntity> found = repository.findById("RFID-TEST-01");
        assertThat(found).isPresent();
        assertThat(found.get().getBlocked()).isFalse();
    }

    @Test
    void findByIdTag_returnsEntity() {
        AuthorizedTagEntity tag = AuthorizedTagEntity.builder()
                .idTag("RFID-FIND-ME")
                .blocked(false)
                .expiryDate(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();
        repository.save(tag);

        Optional<AuthorizedTagEntity> found = repository.findByIdTag("RFID-FIND-ME");
        assertThat(found).isPresent();
        assertThat(found.get().getExpiryDate()).isNotNull();
    }

    @Test
    void findByIdTag_returnsEmptyWhenMissing() {
        Optional<AuthorizedTagEntity> found = repository.findByIdTag("DOES-NOT-EXIST");
        assertThat(found).isEmpty();
    }

    @Test
    void blockedFlagPersistsCorrectly() {
        AuthorizedTagEntity tag = AuthorizedTagEntity.builder()
                .idTag("RFID-BLOCKED-01")
                .blocked(true)
                .createdAt(Instant.now())
                .build();
        repository.save(tag);

        Optional<AuthorizedTagEntity> found = repository.findByIdTag("RFID-BLOCKED-01");
        assertThat(found).isPresent();
        assertThat(found.get().getBlocked()).isTrue();
    }

    @Test
    void parentIdTagIsNullableAndPersists() {
        AuthorizedTagEntity parent = AuthorizedTagEntity.builder()
                .idTag("RFID-PARENT")
                .blocked(false)
                .createdAt(Instant.now())
                .build();
        AuthorizedTagEntity child = AuthorizedTagEntity.builder()
                .idTag("RFID-CHILD")
                .parentIdTag("RFID-PARENT")
                .blocked(false)
                .createdAt(Instant.now())
                .build();
        repository.save(parent);
        repository.save(child);

        Optional<AuthorizedTagEntity> found = repository.findByIdTag("RFID-CHILD");
        assertThat(found).isPresent();
        assertThat(found.get().getParentIdTag()).isEqualTo("RFID-PARENT");
    }
}
