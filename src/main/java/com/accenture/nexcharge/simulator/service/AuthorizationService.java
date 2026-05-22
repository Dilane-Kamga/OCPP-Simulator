package com.accenture.nexcharge.simulator.service;

import com.accenture.nexcharge.simulator.model.entity.AuthorizedTagEntity;
import com.accenture.nexcharge.simulator.repository.AuthorizedTagRepository;
import eu.chargetime.ocpp.model.core.AuthorizationStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates whether a given RFID idTag is authorised to start a charging session.
 *
 * <p>On first startup (empty table) the service seeds RFID-0001 through RFID-0020 as
 * unconditionally accepted so that existing demo/test behaviour is preserved.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private static final List<String> DEFAULT_TAGS;

    static {
        List<String> tags = new ArrayList<>(20);
        for (int i = 1; i <= 20; i++) {
            tags.add(String.format("RFID-%04d", i));
        }
        DEFAULT_TAGS = List.copyOf(tags);
    }

    private final AuthorizedTagRepository repository;

    // -----------------------------------------------------------------------
    // Seed
    // -----------------------------------------------------------------------

    /** Seeds default RFID tags when the table is empty (idempotent). */
    @PostConstruct
    @Transactional
    public void seedDefaultTags() {
        if (repository.count() == 0) {
            Instant now = Instant.now();
            List<AuthorizedTagEntity> seeds = DEFAULT_TAGS.stream()
                    .map(tag -> AuthorizedTagEntity.builder()
                            .idTag(tag)
                            .createdAt(now)
                            .blocked(false)
                            .build())
                    .toList();
            repository.saveAll(seeds);
            log.info("[AUTH] Seeded {} default RFID tags into authorized_tags table", seeds.size());
        }
    }

    // -----------------------------------------------------------------------
    // Core logic
    // -----------------------------------------------------------------------

    /**
     * Determine the OCPP {@link AuthorizationStatus} for a tag at a given instant.
     *
     * <ul>
     *   <li>Tag absent → {@code Invalid}</li>
     *   <li>Tag {@code blocked == true} → {@code Blocked}</li>
     *   <li>Tag has a non-null {@code expiryDate} that is before {@code now} → {@code Expired}</li>
     *   <li>Otherwise → {@code Accepted}</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public AuthorizationStatus authorize(String idTag, Instant now) {
        return repository.findByIdTag(idTag)
                .map(tag -> evaluate(tag, now))
                .orElse(AuthorizationStatus.Invalid);
    }

    private AuthorizationStatus evaluate(AuthorizedTagEntity tag, Instant now) {
        if (Boolean.TRUE.equals(tag.getBlocked())) {
            return AuthorizationStatus.Blocked;
        }
        if (tag.getExpiryDate() != null && tag.getExpiryDate().isBefore(now)) {
            return AuthorizationStatus.Expired;
        }
        return AuthorizationStatus.Accepted;
    }
}
