package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.model.dto.StatsDto;
import com.accenture.nexcharge.simulator.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService service;

    @GetMapping
    public StatsDto get() {
        return service.compute();
    }
}
