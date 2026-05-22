package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.AuthorizedTagDto;
import com.accenture.nexcharge.simulator.model.dto.AuthorizedTagRequest;
import com.accenture.nexcharge.simulator.model.entity.AuthorizedTagEntity;
import com.accenture.nexcharge.simulator.repository.AuthorizedTagRepository;
import com.accenture.nexcharge.simulator.service.TagNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * CRUD management for the RFID tag Access Control List.
 * Base path: {@code /api/auth/tags}.
 */
@RestController
@RequestMapping("/api/auth/tags")
@RequiredArgsConstructor
public class AuthController {

    private final AuthorizedTagRepository repository;

    // -----------------------------------------------------------------------
    // GET /api/auth/tags
    // -----------------------------------------------------------------------

    @GetMapping
    public List<AuthorizedTagDto> listAll() {
        return repository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    // -----------------------------------------------------------------------
    // GET /api/auth/tags/{idTag}
    // -----------------------------------------------------------------------

    @GetMapping("/{idTag}")
    public AuthorizedTagDto getOne(@PathVariable String idTag) {
        return repository.findByIdTag(idTag)
                .map(this::toDto)
                .orElseThrow(() -> new TagNotFoundException(idTag));
    }

    // -----------------------------------------------------------------------
    // POST /api/auth/tags   → 201 Created / 409 Conflict
    // -----------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<AuthorizedTagDto> create(@Valid @RequestBody AuthorizedTagRequest req) {
        if (repository.existsById(req.idTag())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        AuthorizedTagEntity saved = repository.save(fromRequest(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    // -----------------------------------------------------------------------
    // PUT /api/auth/tags/{idTag}   → 200 / 404
    // -----------------------------------------------------------------------

    @PutMapping("/{idTag}")
    public AuthorizedTagDto update(@PathVariable String idTag, @Valid @RequestBody AuthorizedTagRequest req) {
        AuthorizedTagEntity existing = repository.findByIdTag(idTag)
                .orElseThrow(() -> new TagNotFoundException(idTag));

        existing.setParentIdTag(req.parentIdTag());
        existing.setExpiryDate(req.expiryDate());
        existing.setBlocked(req.blocked() != null ? req.blocked() : false);
        return toDto(repository.save(existing));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/auth/tags/{idTag}   → 204 / 404
    // -----------------------------------------------------------------------

    @DeleteMapping("/{idTag}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String idTag) {
        if (!repository.existsById(idTag)) {
            throw new TagNotFoundException(idTag);
        }
        repository.deleteById(idTag);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AuthorizedTagEntity fromRequest(AuthorizedTagRequest req) {
        return AuthorizedTagEntity.builder()
                .idTag(req.idTag())
                .parentIdTag(req.parentIdTag())
                .expiryDate(req.expiryDate())
                .createdAt(Instant.now())
                .blocked(req.blocked() != null ? req.blocked() : false)
                .build();
    }

    private AuthorizedTagDto toDto(AuthorizedTagEntity e) {
        return new AuthorizedTagDto(
                e.getIdTag(),
                e.getParentIdTag(),
                e.getExpiryDate(),
                e.getCreatedAt(),
                e.getBlocked()
        );
    }
}
