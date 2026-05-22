package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.entity.AuthorizedTagEntity;
import com.accenture.nexcharge.simulator.repository.AuthorizedTagRepository;
import eu.chargetime.ocpp.model.core.AuthorizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    AuthorizedTagRepository repository;

    AuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationService(repository);
    }

    // ------------------------------------------------------------------
    // missing tag → Invalid
    // ------------------------------------------------------------------

    @Test
    void missingTag_returnsInvalid() {
        when(repository.findByIdTag("UNKNOWN-TAG")).thenReturn(Optional.empty());

        AuthorizationStatus result = service.authorize("UNKNOWN-TAG", Instant.now());

        assertThat(result).isEqualTo(AuthorizationStatus.Invalid);
    }

    // ------------------------------------------------------------------
    // blocked tag → Blocked
    // ------------------------------------------------------------------

    @Test
    void blockedTag_returnsBlocked() {
        AuthorizedTagEntity tag = AuthorizedTagEntity.builder()
                .idTag("RFID-BLOCKED")
                .blocked(true)
                .createdAt(Instant.now())
                .build();
        when(repository.findByIdTag("RFID-BLOCKED")).thenReturn(Optional.of(tag));

        AuthorizationStatus result = service.authorize("RFID-BLOCKED", Instant.now());

        assertThat(result).isEqualTo(AuthorizationStatus.Blocked);
    }

    // ------------------------------------------------------------------
    // expired tag → Expired
    // ------------------------------------------------------------------

    @Test
    void expiredTag_returnsExpired() {
        AuthorizedTagEntity tag = AuthorizedTagEntity.builder()
                .idTag("RFID-EXPIRED")
                .blocked(false)
                .expiryDate(Instant.now().minusSeconds(60))   // 1 minute in the past
                .createdAt(Instant.now())
                .build();
        when(repository.findByIdTag("RFID-EXPIRED")).thenReturn(Optional.of(tag));

        AuthorizationStatus result = service.authorize("RFID-EXPIRED", Instant.now());

        assertThat(result).isEqualTo(AuthorizationStatus.Expired);
    }

    // ------------------------------------------------------------------
    // valid tag (no expiry, not blocked) → Accepted
    // ------------------------------------------------------------------

    @Test
    void validTag_returnsAccepted() {
        AuthorizedTagEntity tag = AuthorizedTagEntity.builder()
                .idTag("RFID-0001")
                .blocked(false)
                .expiryDate(null)
                .createdAt(Instant.now())
                .build();
        when(repository.findByIdTag("RFID-0001")).thenReturn(Optional.of(tag));

        AuthorizationStatus result = service.authorize("RFID-0001", Instant.now());

        assertThat(result).isEqualTo(AuthorizationStatus.Accepted);
    }

    // ------------------------------------------------------------------
    // tag with future expiry → Accepted (not yet expired)
    // ------------------------------------------------------------------

    @Test
    void tagWithFutureExpiry_returnsAccepted() {
        AuthorizedTagEntity tag = AuthorizedTagEntity.builder()
                .idTag("RFID-0002")
                .blocked(false)
                .expiryDate(Instant.now().plusSeconds(3600))  // 1 hour in the future
                .createdAt(Instant.now())
                .build();
        when(repository.findByIdTag("RFID-0002")).thenReturn(Optional.of(tag));

        AuthorizationStatus result = service.authorize("RFID-0002", Instant.now());

        assertThat(result).isEqualTo(AuthorizationStatus.Accepted);
    }
}
