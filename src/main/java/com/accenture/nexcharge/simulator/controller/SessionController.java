package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.SessionDto;
import com.accenture.nexcharge.simulator.model.enums.SessionStatus;
import com.accenture.nexcharge.simulator.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService service;

    @GetMapping
    public List<SessionDto> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String chargePointId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        SessionStatus parsed = parseStatus(status);
        return service.search(parsed, chargePointId, from, to);
    }

    @GetMapping("/active")
    public List<SessionDto> getActive() {
        return service.findActive();
    }

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
