package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.SessionDto;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Sessions", description = "Charging sessions (active and historical)")
public class SessionController {

    private final SessionService service;

    @Operation(summary = "Search sessions with optional filters (status, chargePointId, from, to, limit, offset)")
    @GetMapping
    public List<SessionDto> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String chargePointId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "100") @Min(1) @Max(1000) int limit,
            @RequestParam(required = false, defaultValue = "0") @Min(0) int offset) {
        SessionStatus parsed = parseStatus(status);
        return service.search(parsed, chargePointId, from, to, limit, offset);
    }

    @Operation(summary = "List all currently active charging sessions")
    @GetMapping("/active")
    public List<SessionDto> getActive() {
        return service.findActive();
    }

    @Operation(summary = "Get a single session by database ID")
    @GetMapping("/{id}")
    public SessionDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    private static SessionStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) return null;
        try {
            return SessionStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }
}
