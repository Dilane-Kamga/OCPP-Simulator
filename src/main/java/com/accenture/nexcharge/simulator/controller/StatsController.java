package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "Aggregated dashboard metrics")
public class StatsController {

    private final StatsService service;

    @Operation(summary = "Get aggregated fleet statistics (online bornes, energy today, active sessions, etc.)")
    @GetMapping
    public StatsDto get() {
        return service.compute();
    }
}
